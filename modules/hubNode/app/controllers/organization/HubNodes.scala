package controllers.organization

import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._
import extensions.Formatters._
import models.{ OrganizationConfiguration, HubNode }
import extensions.JJson
import org.bson.types.ObjectId
import controllers.OrganizationController
import play.api.data.validation._
import play.api.i18n.Messages
import play.api.data.validation.ValidationError
import core.{ DomainServiceLocator, HubModule }
import core.node.{ NodeDirectoryService, NodeSubscriptionService, NodeRegistrationService }
import com.escalatesoft.subcut.inject.BindingModule

class HubNodes(implicit val bindingModule: BindingModule) extends OrganizationController {

  val nodeRegistrationServiceLocator = inject[DomainServiceLocator[NodeRegistrationService]]
  val nodeDirectoryServiceLocator = inject[DomainServiceLocator[NodeDirectoryService]]

  val broadcastingNodeSubscriptionService = inject[NodeSubscriptionService]

  def list = OrganizationAdmin {
    implicit request =>
      val nodes: Seq[HubNodeViewModel] = HubNode.dao.findAll.map(HubNodeViewModel(_))
      Ok(Template('data -> JJson.generate(Map("nodes" -> nodes.filterNot(_.nodeId == configuration.node.nodeId)))))
  }

  def hubNode(id: Option[ObjectId]) = OrganizationAdmin {
    implicit request =>
      val node = id.flatMap { nodeId => HubNode.dao.findOneById(nodeId) }
      val data = node.map(HubNodeViewModel(_))

      data match {
        case Some(d) =>
          val members = nodeRegistrationServiceLocator.byDomain.listMembers(node.get)
          Ok(Template(
            'data -> JJson.generate(d),
            'nodeId -> d.id.get,
            'members -> JJson.generate(members.map(m => Map("id" -> m, "name" -> m)))
          ))
        case None =>
          Ok(Template('data -> JJson.generate(HubNodeViewModel())))
      }
  }

  def delete(id: ObjectId) = OrganizationAdmin {
    implicit request =>
      HubNode.dao.findOneById(id).map { node =>
        try {
          nodeRegistrationServiceLocator.byDomain.removeNode(node)
          HubNode.dao.removeById(id)
          Ok
        } catch {
          case t: Throwable =>
            BadRequest
        }
      }.getOrElse {
        NotFound
      }
  }

  def submit = OrganizationAdmin {
    implicit request =>

      def update(viewModel: HubNodeViewModel, existingNode: HubNode) = {
        // only update the node name, not the ID!
        val updatedNode = existingNode.copy(
          name = viewModel.name
        )
        nodeRegistrationServiceLocator.byDomain.updateNode(updatedNode)
        HubNode.dao.save(updatedNode)
        Right(viewModel)
      }

      def create(viewModel: HubNodeViewModel) = {
        val newNode = HubNode(
          nodeId = slugify(viewModel.name),
          name = viewModel.name,
          orgId = viewModel.orgId
        )

        nodeRegistrationServiceLocator.byDomain.registerNode(newNode, connectedUser)
        HubNode.dao.insert(newNode)

        // connect to our hub
        broadcastingNodeSubscriptionService.generateSubscriptionRequest(configuration.node, newNode)

        Right(HubNodeViewModel(newNode))
      }

      handleSubmit[HubNodeViewModel, HubNode](HubNodeViewModel.virtualNodeForm, HubNode.dao.findOneById, update, create)
  }

  def addMember(id: ObjectId) = OrganizationAdmin {
    implicit request =>
      HubNode.dao.findOneById(id).flatMap { node =>
        val member = request.body.getFirstAsString("id")
        member.map { m =>
          nodeRegistrationServiceLocator.byDomain.addMember(node, m)
          Ok
        }
      }.getOrElse(BadRequest)
  }

  def removeMember(id: ObjectId) = OrganizationAdmin {
    implicit request =>
      HubNode.dao.findOneById(id).flatMap { node =>
        val member = request.body.getFirstAsString("id")
        member.map { m =>
          nodeRegistrationServiceLocator.byDomain.removeMember(node, m)
          Ok
        }
      }.getOrElse(BadRequest)
  }

  case class HubNodeViewModel(
    id: Option[ObjectId] = None,
    nodeId: String = "",
    orgId: String = "",
    name: String = "")

  object HubNodeViewModel {

    def apply(n: HubNode): HubNodeViewModel = HubNodeViewModel(Some(n._id), n.nodeId, n.orgId, n.name)

    def nodeIdTaken(implicit configuration: OrganizationConfiguration) = Constraint[HubNodeViewModel]("hubnode.ANodeWithTheSameNodeIdentifier") {
      case r =>
        val maybeOne = nodeDirectoryServiceLocator.byDomain.findOneById(slugify(r.name))
        if (maybeOne.isDefined && r.id.isDefined) {
          Valid
        } else if (maybeOne == None) {
          Valid
        } else {
          Invalid(ValidationError(Messages("hubnode.ANodeWithTheSameNodeIdentifier")))
        }
    }

    def orgIdValid(implicit configuration: OrganizationConfiguration) = Constraint[String]("hubnode.ThisOrganizationDoesntExist") {
      case r if organizationServiceLocator.byDomain.exists(r) => Valid
      case _ => Invalid(ValidationError(Messages("hubnode.ThisOrganizationDoesntExist")))

    }

    def virtualNodeForm(implicit configuration: OrganizationConfiguration) = Form(
      mapping(
        "id" -> optional(of[ObjectId]),
        "nodeId" -> text,
        "orgId" -> nonEmptyText.verifying(orgIdValid),
        "name" -> nonEmptyText.verifying(Constraints.pattern("^[A-Za-z0-9- ]{3,40}$".r, "hubnode.InvalidNodeName", "hubnode.InvalidNodeName"))
      )(HubNodeViewModel.apply)(HubNodeViewModel.unapply).verifying(nodeIdTaken)
    )
  }

}