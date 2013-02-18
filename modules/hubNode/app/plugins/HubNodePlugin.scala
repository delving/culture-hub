package plugins

import _root_.services.HubNodeSubscriptionService
import play.api.{ Logger, Play, Application }
import play.api.Play.current
import models.{ OrganizationConfiguration, HubNode, Role }
import scala.collection.immutable.ListMap
import scala.util.matching.Regex
import play.api.mvc.Handler
import org.bson.types.ObjectId
import util.OrganizationConfigurationHandler
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
      titleKey = "plugin.hubNode.hubNodes",
      roles = Seq(Role.OWN),
      items = Seq(
        MenuElement("/organizations/%s/hubNode".format(configuration.orgId), "plugin.hubNode.list"),
        MenuElement("/organizations/%s/hubNode/add".format(configuration.orgId), "plugin.hubNode.create")
      )
    )
  )

  override val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap(
    ("GET", """^/organizations/([A-Za-z0-9-]+)/hubNode""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.HubNodes.list
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/hubNode/add""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.HubNodes.hubNode(None)
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/hubNode/([A-Za-z0-9-_]+)/update""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.HubNodes.hubNode(Some(new ObjectId(pathArgs(1))))
    },
    ("POST", """^/organizations/([A-Za-z0-9-]+)/hubNode/submit""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.HubNodes.submit
    },
    ("POST", """^/organizations/([A-Za-z0-9-]+)/hubNode/([A-Za-z0-9-_]+)/addMember""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.HubNodes.addMember(new ObjectId(pathArgs(1)))
    },
    ("DELETE", """^/organizations/([A-Za-z0-9-]+)/hubNode/([A-Za-z0-9-_]+)/removeMember""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.HubNodes.removeMember(new ObjectId(pathArgs(1)))
    },
    ("DELETE", """^/organizations/([A-Za-z0-9-]+)/hubNode/([A-Za-z0-9-_]+)/remove""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.HubNodes.delete(new ObjectId(pathArgs(1)))
    }
  )

  override def onStart() {
    if (Play.isTest || Play.isDev) {
      OrganizationConfigurationHandler.organizationConfigurations.foreach { organizationConfiguration =>
        val service = HubServices.nodeRegistrationServiceLocator.byDomain(organizationConfiguration)
        if (service.isInstanceOf[MemoryServices]) {
          HubNode.dao(organizationConfiguration).findAll.foreach { node =>
            service.registerNode(node, "system")
          }
        }
      }
    }

    lazy val nodeDirectoryServiceLocator = HubModule.inject[DomainServiceLocator[NodeDirectoryService]](name = None)
    lazy val nodeRegistrationServiceLocator = HubModule.inject[DomainServiceLocator[NodeRegistrationService]](name = None)

    // check if we have a HubNode for the hub itself, and create it if necessary
    OrganizationConfigurationHandler.organizationConfigurations.foreach { implicit configuration =>
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
          } catch {
            case t: Throwable =>
              error("Cannot register node for hub", t)
              System.exit(-1)
          }
        } else {
          error("System is in inconsistent state: node '%s' for hub is registered, but no local HubNode can be found".format(configuration.node.nodeId))
          System.exit(-1)
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
