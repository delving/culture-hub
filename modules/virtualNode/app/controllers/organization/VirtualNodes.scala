package controllers.organization

import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import extensions.Formatters._
import models.{DomainConfiguration, VirtualNode}
import extensions.JJson
import org.bson.types.ObjectId
import controllers.OrganizationController
import play.api.data.validation.{ValidationError, Invalid, Valid, Constraint}
import play.api.i18n.Messages

/**
 * TODO verify that node with nodeKey and orgId doesn't exist on creation
 */
object VirtualNodes extends OrganizationController {

  def list = OrganizationAdmin {
    Action {
      implicit request =>
        val nodes: Seq[VirtualNodeViewModel] = VirtualNode.dao.findAll.map(VirtualNodeViewModel(_))
        Ok(Template('data -> JJson.generate(Map("nodes" -> nodes))))
    }
  }

  def virtualNode(id: Option[ObjectId]) = OrganizationAdmin {
    Action {
      implicit request =>
        val node = id.flatMap { nodeId => VirtualNode.dao.findOneById(nodeId) }
        val data = node.map(VirtualNodeViewModel(_))

        data match {
          case Some(d) => Ok(Template('data -> JJson.generate(d)))
          case None => Ok(Template('data -> JJson.generate(VirtualNodeViewModel())))
        }
    }
  }

  def delete(id: ObjectId) = OrganizationAdmin {
    Action {
      implicit request =>
        VirtualNode.dao.removeById(id)
        Ok
    }
  }

  def submit = OrganizationAdmin {
    Action {
      implicit request =>
        VirtualNodeViewModel.virtualNodeForm.bind(request.body.asJson.get).fold(
          formWithErrors => handleValidationError(formWithErrors),
          virtualNodeForm => {
            val maybeNode = virtualNodeForm.id match {
              case Some(id) =>
                VirtualNode.dao.findOneById(id) match {
                  case Some(existingNode) =>
                    val updatedNode = existingNode.copy(
                      name = virtualNodeForm.name
                    )
                    VirtualNode.dao.save(updatedNode)
                    Some(updatedNode)
                  case None =>
                    None
                }
              case None =>
                val newNode = VirtualNode(
                  nodeId = virtualNodeForm.nodeId,
                  name = virtualNodeForm.name,
                  orgId = virtualNodeForm.orgId
                )

                VirtualNode.dao.insert(newNode)
                Some(newNode)
            }

            maybeNode match {
              case Some(n) => Json(n)
              case None => BadRequest
            }
          }
        )
    }
  }


  case class VirtualNodeViewModel(
    id: Option[ObjectId] = None,
    nodeId: String = "",
    orgId: String = "",
    name: String = "",
    errors: Map[String, String] = Map.empty
  )

  object VirtualNodeViewModel {

    def apply(n: VirtualNode): VirtualNodeViewModel = VirtualNodeViewModel(Some(n._id), n.nodeId, n.orgId, n.name)

    def nodeIdTaken(implicit configuration: DomainConfiguration) = Constraint[VirtualNodeViewModel]("plugin.virtualNode.nodeIdTaken") {
        case r =>
          val maybeOne = VirtualNode.dao.findOne(r.orgId, r.nodeId)
          val maybeOneId = maybeOne.map(_._id)
          if (maybeOneId.isDefined && r.id.isDefined && maybeOneId == r.id) {
            Valid
          } else if (maybeOne == None) {
            Valid
          } else {
            Invalid(ValidationError(Messages("plugin.virtualNode.nodeIdTaken")))
          }
    }


    def virtualNodeForm(implicit configuration: DomainConfiguration) = Form(
      mapping(
        "id" -> optional(of[ObjectId]),
        "nodeId" -> nonEmptyText,
        "orgId" -> nonEmptyText,
        "name" -> nonEmptyText,
        "errors" -> of[Map[String, String]]
      )(VirtualNodeViewModel.apply)(VirtualNodeViewModel.unapply).verifying(nodeIdTaken)
    )
  }


}