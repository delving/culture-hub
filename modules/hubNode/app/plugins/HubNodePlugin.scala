package plugins

import _root_.services.HubNodeSubscriptionService
import core.node.NodeSubscriptionService
import play.api.{Play, Application}
import play.api.Play.current
import models.{HubNode, Role}
import scala.collection.immutable.ListMap
import scala.util.matching.Regex
import play.api.mvc.Handler
import org.bson.types.ObjectId
import util.DomainConfigurationHandler
import core.services.MemoryServices
import core._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class HubNodePlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "hubNode"

  override def organizationMenuEntries(orgId: String, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "hubNode",
      titleKey = "plugin.hubNode.hubNodes",
      roles = Seq(Role.OWN),
      items = Seq(
        MenuElement("/organizations/%s/hubNode".format(orgId), "plugin.hubNode.list"),
        MenuElement("/organizations/%s/hubNode/add".format(orgId), "plugin.hubNode.create")
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
      DomainConfigurationHandler.domainConfigurations.foreach { domainConfiguration =>
        val service = HubServices.nodeRegistrationServiceLocator.byDomain(domainConfiguration)
        if (service.isInstanceOf[MemoryServices]) {
          HubNode.dao(domainConfiguration).findAll.foreach { node =>
            service.registerNode(node, "system")
          }
        }
      }
    }

    val service = HubModule.inject[NodeSubscriptionService](name = None)

    // HubNodeSubscriptionService has amnesia for the time being
    DomainConfigurationHandler.domainConfigurations.foreach { implicit configuration =>
      HubNode.dao.findAll.foreach { node =>
        service.processSubscriptionRequest(configuration.node, node)
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
