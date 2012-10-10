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
import controllers.{BoundController, OrganizationController}
import play.api.data.validation._
import play.api.i18n.Messages
import scala.Some
import play.api.data.validation.ValidationError
import core.{DomainServiceLocator, HubModule}
import core.node.NodeRegistrationService

object VirtualNodes extends BoundController(HubModule) with VirtualNodes

trait VirtualNodes extends OrganizationController { self: BoundController =>

  val nodeRegistrationServiceLocator = inject [ DomainServiceLocator[NodeRegistrationService] ]

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
          case Some(d) =>
            val members = nodeRegistrationServiceLocator.byDomain.listMembers(node.get)
            Ok(Template(
              'data -> JJson.generate(d),
              'nodeId -> d.id.get,
              'members -> JJson.generate(members.map(m => Map("id" -> m, "name" -> m)))
            ))
          case None =>
            Ok(Template('data -> JJson.generate(VirtualNodeViewModel())))
        }
    }
  }

  def delete(id: ObjectId) = OrganizationAdmin {
    Action {
      implicit request =>
          VirtualNode.dao.findOneById(id).map { node =>
            try {
              nodeRegistrationServiceLocator.byDomain.removeNode(node)
              VirtualNode.dao.removeById(id)
              Ok
            } catch {
             case t: Throwable =>
               BadRequest
            }
        }.getOrElse {
          NotFound
        }
    }
  }

  def submit = OrganizationAdmin {
    Action {
      implicit request =>
        VirtualNodeViewModel.virtualNodeForm.bind(request.body.asJson.get).fold(
          formWithErrors => handleValidationError(formWithErrors),
          viewModel => {
            viewModel.id match {
              case Some(id) =>
                VirtualNode.dao.findOneById(id) match {
                  case Some(existingNode) =>
                    try {
                      nodeRegistrationServiceLocator.byDomain.updateNode(existingNode)
                      // only update the node name, not the ID!
                      val updatedNode = existingNode.copy(
                        name = viewModel.name
                      )
                      VirtualNode.dao.save(updatedNode)
                      Json(viewModel)
                    } catch {
                      case t: Throwable =>
                        Json(viewModel.copy(errors = Map("global" -> t.getMessage)))
                    }

                  case None =>
                    Json(viewModel.copy(errors = Map("global" -> "Node could not be found! Maybe it was deleted by somebody else in the meantime ?")))
                }
              case None =>
                val newNode = VirtualNode(
                  nodeId = slugify(viewModel.name),
                  name = viewModel.name,
                  orgId = viewModel.orgId
                )

                try {
                  nodeRegistrationServiceLocator.byDomain.registerNode(newNode, connectedUser)
                  VirtualNode.dao.insert(newNode)
                  Json(VirtualNodeViewModel(newNode))
                } catch {
                  case t: Throwable =>
                    Json(VirtualNodeViewModel(newNode).copy(errors = Map("global" -> t.getMessage)))
                }

            }
          }
        )
    }
  }

  def addMember(id: ObjectId) = OrganizationAdmin {
    Action {
      implicit request =>
        VirtualNode.dao.findOneById(id).flatMap { node =>
          val member = request.body.getFirstAsString("id")
          member.map { m =>
            nodeRegistrationServiceLocator.byDomain.addMember(node, m)
            Ok
          }
        }.getOrElse(BadRequest)
    }
  }

  def removeMember(id: ObjectId) = OrganizationAdmin {
    Action {
      implicit request =>
        VirtualNode.dao.findOneById(id).flatMap { node =>
          val member = request.body.getFirstAsString("id")
          member.map { m =>
            nodeRegistrationServiceLocator.byDomain.removeMember(node, m)
            Ok
          }
        }.getOrElse(BadRequest)
    }
  }


  def slugify(str: String): String = {
    import java.text.Normalizer
    Normalizer.normalize(str, Normalizer.Form.NFD).replaceAll("[^\\w ]", "").replace(" ", "-").toLowerCase
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
        "nodeId" -> text,
        "orgId" -> nonEmptyText.verifying(Constraints.pattern("^[A-Za-z0-9-]{3,40}$".r, "plugin.virtualNode.invalidOrgId", "plugin.virtualNode.invalidOrgId")),
        "name" -> nonEmptyText.verifying(Constraints.pattern("^[A-Za-z0-9- ]{3,40}$".r, "plugin.virtualNode.invalidNodeId", "plugin.virtualNode.invalidNodeName")),
        "errors" -> of[Map[String, String]]
      )(VirtualNodeViewModel.apply)(VirtualNodeViewModel.unapply).verifying(nodeIdTaken)
    )
  }


}