package controllers.organization

import play.api.mvc._
import models.VirtualCollection
import org.bson.types.ObjectId
import controllers.{ViewModel, OrganizationController}
import play.api.data.Forms._
import play.api.data.Form
import extensions.JJson
import extensions.Formatters._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object VirtualCollections extends OrganizationController {

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
            case Some(vc) => Some(VirtualCollectionViewModel(Some(vc._id), vc.spec, vc.name))
            case None => None
          }
          case None => Some(VirtualCollectionViewModel(None, "", ""))
        }

        if(viewModel.isEmpty) {
          NotFound(spec.getOrElse(""))
        } else {
          Ok(Template('id -> spec, 'data -> JJson.generate(viewModel.get), 'virtualCollectionForm -> VirtualCollectionViewModel.virtualCollectionForm))
        }
    }
  }

  def submit(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>

        VirtualCollectionViewModel.virtualCollectionForm.bindFromRequest.fold(
          formWithErrors => handleValidationError(formWithErrors),
          virtualCollectionForm => {
            virtualCollectionForm.id match {
              case Some(id) => Ok
              case None => Ok
            }
            Json(virtualCollectionForm)
          }
        )
    }
  }

}



case class VirtualCollectionViewModel(id: Option[ObjectId] = None,
                                      spec: String,
                                      name: String,
                                      errors: Map[String, String] = Map.empty[String, String]) extends ViewModel

object VirtualCollectionViewModel {

  val virtualCollectionForm = Form(
    mapping(
      "id" -> optional(of[ObjectId]),
      "spec" -> nonEmptyText,
      "name" -> nonEmptyText,
      "errors" -> of[Map[String, String]]
    )(VirtualCollectionViewModel.apply)(VirtualCollectionViewModel.unapply)
  )

}