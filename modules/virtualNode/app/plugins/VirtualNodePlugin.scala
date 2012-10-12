package plugins

import _root_.services.VirtualNodeSubscriptionService
import core._
import core.node.NodeSubscriptionService
import play.api.{Play, Application}
import play.api.Play.current
import models.{VirtualNode, Role}
import scala.collection.immutable.ListMap
import scala.util.matching.Regex
import play.api.mvc.Handler
import org.bson.types.ObjectId
import util.DomainConfigurationHandler
import core.services.MemoryServices
import core._
import core.MainMenuEntry
import scala.Some
import core.MenuElement

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class VirtualNodePlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "virtualNode"

  override def organizationMenuEntries(orgId: String, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "virtualNode",
      titleKey = "plugin.virtualNode.virtualNodes",
      roles = Seq(Role.OWN),
      items = Seq(
        MenuElement("/organizations/%s/virtualNode".format(orgId), "plugin.virtualNode.list"),
        MenuElement("/organizations/%s/virtualNode/add".format(orgId), "plugin.virtualNode.create")
      )
    )
  )

  override val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap(
    ("GET", """^/organizations/([A-Za-z0-9-]+)/virtualNode""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.VirtualNodes.list
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/virtualNode/add""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.VirtualNodes.virtualNode(None)
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/virtualNode/([A-Za-z0-9-_]+)/update""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.VirtualNodes.virtualNode(Some(new ObjectId(pathArgs(1))))
    },
    ("POST", """^/organizations/([A-Za-z0-9-]+)/virtualNode/submit""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.VirtualNodes.submit
    },
    ("POST", """^/organizations/([A-Za-z0-9-]+)/virtualNode/([A-Za-z0-9-_]+)/addMember""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.VirtualNodes.addMember(new ObjectId(pathArgs(1)))
    },
    ("DELETE", """^/organizations/([A-Za-z0-9-]+)/virtualNode/([A-Za-z0-9-_]+)/removeMember""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.VirtualNodes.removeMember(new ObjectId(pathArgs(1)))
    },
    ("DELETE", """^/organizations/([A-Za-z0-9-]+)/virtualNode/([A-Za-z0-9-_]+)/remove""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.VirtualNodes.delete(new ObjectId(pathArgs(1)))
    }
  )

  override def onStart() {
    if (Play.isTest || Play.isDev) {
      DomainConfigurationHandler.domainConfigurations.foreach { domainConfiguration =>
        val service = HubServices.nodeRegistrationServiceLocator.byDomain(domainConfiguration)
        if (service.isInstanceOf[MemoryServices]) {
          VirtualNode.dao(domainConfiguration).findAll.foreach { node =>
            service.registerNode(node, "system")
          }
        }
      }
    }

    val service = HubModule.inject[NodeSubscriptionService](name = None)

    // HubNodeSubscriptionService has amnesia for the time being
    DomainConfigurationHandler.domainConfigurations.foreach { implicit configuration =>
      VirtualNode.dao.findAll.foreach { node =>
        service.processSubscriptionRequest(configuration.node, node)
      }
    }

  }

  /**
   * Service instances this plugin provides
   */
  override def services: Seq[Any] = Seq(VirtualNodePlugin.virtualNodeConnectionService)
}

object VirtualNodePlugin {

  lazy val virtualNodeConnectionService: NodeSubscriptionService = new VirtualNodeSubscriptionService()(HubModule)

}
