package controllers.organization

import play.api.mvc._
import org.bson.types.ObjectId
import play.api.data.Forms._
import play.api.data.Form
import extensions.JJson
import extensions.Formatters._
import core.search._
import play.api.Logger
import collection.mutable.ListBuffer
import core.Constants._
import models._
import controllers.{Token, ShortDataSet, ViewModel, OrganizationController}

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
              'queryDatasets -> vc.query.dataSets,
              'referencedDatasets -> vc.dataSetReferences.map(_.spec).toList,
              'recordCount -> vc.recordCount
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
            case Some(vc) => Some(VirtualCollectionViewModel(Some(vc._id), vc.spec, vc.name, vc.query.dataSets.map(r => Token(r, r)), vc.query.freeFormQuery, vc.query.excludeHubIds.mkString(",")))
            case None => None
          }
          case None => Some(VirtualCollectionViewModel(None, "", "", List.empty, "", ""))
        }

        if (viewModel.isEmpty) {
          NotFound(spec.getOrElse(""))
        } else {
          Ok(Template(
            'id -> spec,
            'data -> JJson.generate(viewModel.get),
            'virtualCollectionForm -> VirtualCollectionViewModel.virtualCollectionForm,
            'dataSets -> JJson.generate(viewModel.get.dataSets)
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
            val virtualCollectionQuery = VirtualCollectionQuery(
              virtualCollectionForm.dataSets.map(_.id),
              virtualCollectionForm.freeFormQuery,
              virtualCollectionForm.excludedIdentifiers.split(",").map(_.trim).filterNot(_.isEmpty).toList
            )
            virtualCollectionForm.id match {
              case Some(id) =>
                VirtualCollection.findOneByID(id) match {
                  case Some(vc) =>

                    // clear the previous entries
                    VirtualCollection.children.removeByParentId(id)

                    // create new virtual collection
                    createVirtualCollectionFromQuery(id, virtualCollectionQuery.toSolrQuery, theme) match {
                      case Right(u) =>
                      // update collection definition
                      val updated = u.copy(
                        spec = virtualCollectionForm.spec,
                        name = virtualCollectionForm.name,
                        query = virtualCollectionQuery,
                        currentQueryCount = VirtualCollection.children.countByParentId(u._id)
                      )
                    VirtualCollection.save(updated)


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
                            query = virtualCollectionQuery,
                            dataSetReferences = List.empty)
                val id = VirtualCollection.insert(vc)
                id match {
                  case Some(vcid) =>
                    createVirtualCollectionFromQuery(vcid, virtualCollectionQuery.toSolrQuery, theme) match {
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

  private def createVirtualCollectionFromQuery(id: ObjectId, query: String, theme: PortalTheme)(implicit request: RequestHeader): Either[Throwable, VirtualCollection] = {
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
              val cache = MetadataCache.get(orgId, spec, ITEM_TYPE_MDR)
              cache.iterate().foreach {
                item =>
                  val ref = MDRReference(parentId = id, collection = spec, itemId = item.itemId, invalidTargetSchemas = item.invalidTargetSchemas, index = item.index)
                  VirtualCollection.children.insert(ref)
              }
              Some(DataSetReference(spec, orgId))

            case None =>
              Logger("CultureHub").warn("Attempting to add entries to Virtual Collection from non-existing DataSet " + spec)
              None
          }
      }.toList

      val count = VirtualCollection.children.countByParentId(id)

      val updatedVc = vc.copy(dataSetReferences = dataSetReferences, currentQueryCount = count)
      VirtualCollection.save(updatedVc)
      Right(updatedVc)

    } catch {
      case mqe: MalformedQueryException => return Left(mqe)
      case t => return Left(t)
    }
  }

  private def getIdsFromQuery(query: String, start: Int = 0, ids: ListBuffer[String] = ListBuffer.empty)(implicit request: RequestHeader): List[String] = {
    
    // for the start, only pass a dead-simple query
    val params = Params(Map("query" -> Seq(query), "start" -> Seq(start.toString)))
    val chQuery: CHQuery = SolrQueryService.createCHQuery(params, theme, true, Option(connectedUser), List.empty[String])
    val response = CHResponse(params, theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery)
    val briefItemView = BriefItemView(response)
    val hubIds = briefItemView.getBriefDocs.map(_.getHubId).filterNot(_.isEmpty)
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
                                      dataSets: List[Token] = List.empty[Token],
                                      freeFormQuery: String,
                                      excludedIdentifiers: String, // comma-separated list of identifiers to be excluded
                                      errors: Map[String, String] = Map.empty[String, String]) extends ViewModel

object VirtualCollectionViewModel {

  val virtualCollectionForm = Form(
    mapping(
      "id" -> optional(of[ObjectId]),
      "spec" -> nonEmptyText,
      "name" -> nonEmptyText,
      "dataSets" -> VirtualCollections.tokenListMapping,
      "freeFormQuery" -> text,
      "excludedIdentifiers" -> text,
      "errors" -> of[Map[String, String]]
    )(VirtualCollectionViewModel.apply)(VirtualCollectionViewModel.unapply)
  )

}