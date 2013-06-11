package plugins

import _root_.services.HubNodeSubscriptionService
import play.api.{ Logger, Play, Application }
import play.api.Play.current
import models.{ OrganizationConfiguration, HubNode, Role }
import scala.collection.immutable.ListMap
import scala.util.matching.Regex
import play.api.mvc.Handler
import org.bson.types.ObjectId
import util.{ OrganizationConfigurationResourceHolder, OrganizationConfigurationHandler }
import core.services.MemoryServices
import core._
import node.{ NodeDirectoryService, NodeRegistrationService, NodeSubscriptionService }

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class HubNodePlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "hubNode"

  override def organizationMenuEntries(configuration: OrganizationConfiguration, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "hubNode",
      titleKey = "hubnode.HubNodes",
      roles = Seq(Role.OWN),
      items = Seq(
        MenuElement("/admin/hubNode", "hubnode.ListHubNodes"),
        MenuElement("/admin/hubNode/add", "hubnode.CreateHubNode")
      )
    )
  )

  private val hubNodeController = new controllers.organization.HubNodes()(HubModule)

  override val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap(
    ("GET", """^/admin/hubNode""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => hubNodeController.list
    },
    ("GET", """^/admin/hubNode/add""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => hubNodeController.hubNode(None)
    },
    ("GET", """^/admin/hubNode/([A-Za-z0-9-_]+)/update""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => hubNodeController.hubNode(Some(new ObjectId(pathArgs(0))))
    },
    ("POST", """^/admin/hubNode/submit""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => hubNodeController.submit
    },
    ("POST", """^/admin/hubNode/([A-Za-z0-9-_]+)/addMember""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => hubNodeController.addMember(new ObjectId(pathArgs(0)))
    },
    ("DELETE", """^/admin/hubNode/([A-Za-z0-9-_]+)/removeMember""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => hubNodeController.removeMember(new ObjectId(pathArgs(0)))
    },
    ("DELETE", """^/admin/hubNode/([A-Za-z0-9-_]+)/remove""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => hubNodeController.delete(new ObjectId(pathArgs(0)))
    }
  )

  // note: we abuse the concept of resource holder here and use instead the notification for new organizations
  val hubNodes = new OrganizationConfigurationResourceHolder[OrganizationConfiguration, HubNode]("hubNodes") {

    lazy val nodeDirectoryServiceLocator = HubModule.inject[DomainServiceLocator[NodeDirectoryService]](name = None)
    lazy val nodeRegistrationServiceLocator = HubModule.inject[DomainServiceLocator[NodeRegistrationService]](name = None)

    protected def resourceConfiguration(configuration: OrganizationConfiguration): OrganizationConfiguration = configuration

    protected def onAdd(resourceConfiguration: OrganizationConfiguration): Option[HubNode] = {
      implicit val configuration = resourceConfiguration

      if (HubNode.dao.findOne(configuration.node.nodeId).isEmpty) {
        val registered = nodeDirectoryServiceLocator.byDomain.findOneById(configuration.node.nodeId)
        if (registered.isEmpty) {
          val hubNode = HubNode(
            nodeId = configuration.node.nodeId,
            name = configuration.node.name,
            orgId = configuration.node.orgId
          )
          try {
            info("Attempting to create and register node '%s' for hub".format(configuration.node.nodeId))
            nodeRegistrationServiceLocator.byDomain.registerNode(hubNode, "system")
            HubNode.dao.insert(hubNode)
            info("Node '%s' registered successfully".format(configuration.node.nodeId))
            Some(hubNode)
          } catch {
            case t: Throwable =>
              error("Cannot register node for hub", t)
              None
          }
        } else {
          error("System is in inconsistent state: node '%s' for hub is registered, but no local HubNode can be found".format(configuration.node.nodeId))
          None
        }
      } else {
        HubNode.dao.findOne(configuration.node.nodeId)
      }
    }

    protected def onRemove(removed: HubNode) {}
  }

  override def onStart() {

    OrganizationConfigurationHandler.registerResourceHolder(hubNodes)

    if (Play.isTest || Play.isDev) {
      OrganizationConfigurationHandler.getAllCurrentConfigurations.foreach { implicit organizationConfiguration =>
        val service = HubServices.nodeRegistrationServiceLocator.byDomain(organizationConfiguration)
        if (service.isInstanceOf[MemoryServices]) {
          HubNode.dao.findAll.foreach { node =>
            service.registerNode(node, "system")
          }
        }
      }
    }

  }

  /**
   * Service instances this plugin provides
   */
  override def services: Seq[Any] = Seq(HubNodePlugin.hubNodeConnectionService)
}

object HubNodePlugin {

  lazy val hubNodeConnectionService: NodeSubscriptionService = new HubNodeSubscriptionService()(HubModule)

}