package controllers.organization

import play.api.mvc._
import org.bson.types.ObjectId
import play.api.data.Forms._
import play.api.data.Form
import extensions.JJson
import extensions.Formatters._
import core.search._
import play.api.Logger
import models._
import collection.mutable.ListBuffer
import controllers.{AccessControl, ShortDataSet, ViewModel, OrganizationController}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object VirtualCollections extends OrganizationController {

  def view(orgId: String, spec: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        VirtualCollection.findBySpecAndOrgId(spec, orgId) match {
          case Some(vc) =>
            Ok(Template(
              'spec -> spec,
              'name -> vc.name,
              'dataSets -> vc.dataSetReferences.map(_.spec).toList,
              'recordCount -> VirtualCollection.children.countByParentId(vc._id)
            ))

          case None => NotFound("Could not find Virtual Collection " + spec)
        }

    }

  }

  def list(orgId: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val collections = VirtualCollection.findAll(orgId)
        Ok(Template('virtualCollections -> collections))
    }
  }

  def virtualCollection(orgId: String, spec: Option[String]) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>

        val viewModel = spec match {
          case Some(cid) => VirtualCollection.findBySpecAndOrgId(cid, orgId) match {
            case Some(vc) => Some(VirtualCollectionViewModel(Some(vc._id), vc.spec, vc.name, vc.query.dataSets.mkString(", "), vc.query.excludeHubIds.mkString(","), vc.query.freeFormQuery))
            case None => None
          }
          case None => Some(VirtualCollectionViewModel(None, "", "", "", "", ""))
        }

        val dataSets: List[ShortDataSet] = DataSet.findAllVisible(orgId, connectedUser, request.session(AccessControl.ORGANIZATIONS))

        if (viewModel.isEmpty) {
          NotFound(spec.getOrElse(""))
        } else {
          Ok(Template(
            'id -> spec,
            'data -> JJson.generate(viewModel.get),
            'virtualCollectionForm -> VirtualCollectionViewModel.virtualCollectionForm,
            'dataSets -> dataSets
          ))
        }
    }
  }

  def submit(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>

        VirtualCollectionViewModel.virtualCollectionForm.bind(request.body.asJson.get).fold(
          formWithErrors => handleValidationError(formWithErrors),
          virtualCollectionForm => {
            virtualCollectionForm.id match {
              case Some(id) =>
                VirtualCollection.findOneByID(id) match {
                  case Some(vc) =>
                    // update collection definition
                    val updated = vc.copy(
                      spec = virtualCollectionForm.spec,
                      name = virtualCollectionForm.name,
                      query = VirtualCollectionQuery(
                        virtualCollectionForm.dataSets.split(",").map(_.trim).toList,
                        virtualCollectionForm.freeFormQuery,
                        virtualCollectionForm.excludedIdentifiers.split(",").map(_.trim).toList
                      )
                    )
                    VirtualCollection.save(updated)

                    // clear the previous entries
                    VirtualCollection.children.removeByParentId(id)

                    // create new virtual collection
                    val query = composeQuery(virtualCollectionForm.dataSets, virtualCollectionForm.freeFormQuery, virtualCollectionForm.excludedIdentifiers)
                    createVirtualCollectionFromQuery(id, query, theme) match {
                      case Right(ok) => Ok
                      case Left(t) =>
                        logError(t, "Error while computing virtual collection")
                        Error("Error computing virtual collection")
                    }
                  case None =>
                    NotFound("Could not find VirtualCollection with ID " + id)
                }
              case None =>
                val vc = VirtualCollection(
                            spec = virtualCollectionForm.spec,
                            name = virtualCollectionForm.name,
                            orgId = orgId,
                            query = VirtualCollectionQuery(
                              virtualCollectionForm.dataSets.split(",").map(_.trim).toList,
                              virtualCollectionForm.freeFormQuery,
                              virtualCollectionForm.excludedIdentifiers.split(",").map(_.trim).toList
                            ),
                            dataSetReferences = List.empty)
                val id = VirtualCollection.insert(vc)
                id match {
                  case Some(vcid) =>
                    val query = composeQuery(virtualCollectionForm.dataSets, virtualCollectionForm.freeFormQuery, virtualCollectionForm.excludedIdentifiers)
                    createVirtualCollectionFromQuery(vcid, query, theme) match {
                      case Right(ok) => Ok
                      case Left(t) =>
                        logError(t, "Error while computing virtual collection")
                        Error("Error computing virtual collection")
                    }
                  case None => InternalServerError("Could not create VirtualCollection")
                }
            }
            Json(virtualCollectionForm)
          }
        )
    }
  }

  def delete(orgId: String, spec: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        VirtualCollection.findBySpecAndOrgId(spec, orgId) match {
          case Some(vc) =>
            VirtualCollection.children.removeByParentId(vc._id)
            VirtualCollection.remove(vc)
            Ok
          case None => Results.NotFound
        }

    }
  }

  private def composeQuery(dataSets: String, freeFormQuery: String, excludedIdentifiers: String) = {
    val specCondition = dataSets.split(",").filterNot(_.isEmpty).map(s => "delving_spec:" + s.trim + " ").mkString(" ")
    val excludedIdentifiersCondition = "NOT (" + excludedIdentifiers.split(",").map(s => "delving_hubId:\"" + s.trim + "\"").mkString(" OR ") + ")"
    val query = specCondition + " " + freeFormQuery + " " + excludedIdentifiersCondition
    Logger("CultureHub").debug("Composed query " + query)
    query
  }

  private def createVirtualCollectionFromQuery(id: ObjectId, query: String, theme: PortalTheme)(implicit request: RequestHeader): Either[Throwable, String] = {
    val vc = VirtualCollection.findOneByID(id).getOrElse(return Left(new RuntimeException("Could not find collection with ID " + id)))

    try {
      val hubIds = getIdsFromQuery(query)
      val groupedHubIds = hubIds.groupBy(id => (id.split("_")(0), id.split("_")(1)))

      val dataSetReferences: List[DataSetReference] = groupedHubIds.flatMap {
        specIds =>
          val orgId = specIds._1._1
          val spec = specIds._1._2
          val ids = specIds._2

          DataSet.findBySpecAndOrgId(spec, orgId) match {
            case Some(ds) =>
              val hubCollection = DataSet.getRecordsCollectionName(ds)
              val mdrs = MetadataRecord.getMDRs(hubCollection, ids)

              val references = for (i <- 0 until mdrs.size) yield {
                val mdr = mdrs(i)
                MDRReference(parentId = id, hubCollection = hubCollection, hubId = mdr.hubId, validOutputFormats = mdr.validOutputFormats, idx = i)
              }
              for (ref <- references) {
                VirtualCollection.children.insert(ref)
              }
              Some(DataSetReference(spec, orgId))

            case None =>
              Logger("CultureHub").warn("Attempting to add entries to Virtual Collection from non-existing DataSet " + spec)
              None
          }
      }.toList

      val updatedVc = vc.copy(dataSetReferences = dataSetReferences)
      VirtualCollection.save(updatedVc)

    } catch {
      case mqe: MalformedQueryException => Left(mqe)
      case t => Left(t)
    }

    Right("ok")
  }

  private def getIdsFromQuery(query: String, start: Int = 0, ids: ListBuffer[String] = ListBuffer.empty)(implicit request: RequestHeader): List[String] = {
    
    // for the start, only pass a dead-simple query
    val params = Params(Map("query" -> Seq(query), "start" -> Seq(start.toString)))
    val chQuery: CHQuery = SolrQueryService.createCHQuery(params, theme, true, Option(connectedUser), List.empty[String])
    val response = CHResponse(params, theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery)
    val briefItemView = BriefItemView(response)
    val hubIds = briefItemView.getBriefDocs.map(b => b.getHubId)
    Logger("CultureHub").debug("Found ids " + hubIds)
    ids ++= hubIds

    if(briefItemView.getPagination.isNext) {
      getIdsFromQuery(query, briefItemView.getPagination.getNextPage, ids)
    }
    
    ids.toList

  }

}




case class VirtualCollectionViewModel(id: Option[ObjectId] = None,
                                      spec: String,
                                      name: String,
                                      dataSets: String, // comma-separated list of spec names
                                      excludedIdentifiers: String, // comma-separated list of identifiers to be excluded
                                      freeFormQuery: String,
                                      errors: Map[String, String] = Map.empty[String, String]) extends ViewModel

object VirtualCollectionViewModel {

  val virtualCollectionForm = Form(
    mapping(
      "id" -> optional(of[ObjectId]),
      "spec" -> nonEmptyText,
      "name" -> nonEmptyText,
      "dataSets" -> text,
      "excludedIdentifiers" -> text,
      "freeFormQuery" -> text,
      "errors" -> of[Map[String, String]]
    )(VirtualCollectionViewModel.apply)(VirtualCollectionViewModel.unapply)
  )

}