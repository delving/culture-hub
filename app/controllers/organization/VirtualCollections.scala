package controllers.organization

import play.api.mvc._
import org.bson.types.ObjectId
import play.api.data.Forms._
import play.api.data.Form
import extensions.JJson
import extensions.Formatters._
import models._
import controllers.{Token, ViewModel, OrganizationController}

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
              'autoUpdate -> vc.autoUpdate,
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
            case Some(vc) => Some(VirtualCollectionViewModel(Some(vc._id), vc.spec, vc.name, vc.autoUpdate, vc.query.dataSets.map(r => Token(r, r)), vc.query.freeFormQuery, vc.query.excludeHubIds.mkString(",")))
            case None => None
          }
          case None => Some(VirtualCollectionViewModel(None, "", "", false, List.empty, "", ""))
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
              virtualCollectionForm.excludedIdentifiers.split(",").map(_.trim).filterNot(_.isEmpty).toList,
              theme.name
            )
            virtualCollectionForm.id match {
              case Some(id) =>
                VirtualCollection.findOneById(id) match {
                  case Some(vc) =>

                    // clear the previous entries
                    VirtualCollection.children.removeByParentId(id)

                    // create new virtual collection
                    VirtualCollection.createVirtualCollectionFromQuery(id, virtualCollectionQuery.toSolrQuery, theme, connectedUser) match {
                      case Right(u) =>
                      // update collection definition
                      val updated = u.copy(
                        spec = virtualCollectionForm.spec,
                        name = virtualCollectionForm.name,
                        autoUpdate = virtualCollectionForm.autoUpdate,
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
                            autoUpdate = virtualCollectionForm.autoUpdate,
                            orgId = orgId,
                            query = virtualCollectionQuery,
                            dataSetReferences = List.empty)
                val id = VirtualCollection.insert(vc)
                id match {
                  case Some(vcid) =>
                    VirtualCollection.createVirtualCollectionFromQuery(vcid, virtualCollectionQuery.toSolrQuery, theme, connectedUser) match {
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

case class VirtualCollectionViewModel(id: Option[ObjectId] = None,
                                      spec: String,
                                      name: String,
                                      autoUpdate: Boolean,
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
      "autoUpdate" -> boolean,
      "dataSets" -> VirtualCollections.tokenListMapping,
      "freeFormQuery" -> text,
      "excludedIdentifiers" -> text,
      "errors" -> of[Map[String, String]]
    )(VirtualCollectionViewModel.apply)(VirtualCollectionViewModel.unapply)
  )

}

}