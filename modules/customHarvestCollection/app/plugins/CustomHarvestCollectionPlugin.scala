package plugins

import core.{MenuElement, MainMenuEntry, CultureHubPlugin}
import core.collection.HarvestCollectionLookup
import play.api.mvc.Handler
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.Application
import models.{GrantType, CustomHarvestCollectionHarvestCollectionLookup}
import akka.actor.Props
import akka.util.duration._
import jobs.{UpdateVirtualCollection, UpdateVirtualCollectionCount, VirtualCollectionCount}
import scala.util.matching.Regex
import collection.immutable.ListMap

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CustomHarvestCollectionPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "customHarvestCollection"

  private val customHarvestCollectionHarvestCollectionLookup = new CustomHarvestCollectionHarvestCollectionLookup


  override def onApplicationStart() {
    // virtual collection update
    val virtualCollectionCount = Akka.system.actorOf(Props[VirtualCollectionCount])
    Akka.system.scheduler.schedule(
      1 minute,
      1 hour,
      virtualCollectionCount,
      UpdateVirtualCollectionCount
    )
    Akka.system.scheduler.schedule(
      1 minute,
      2 hours,
      virtualCollectionCount,
      UpdateVirtualCollection
    )

  }


  /*
  GET         /organizations/:orgId/virtualCollection                           controllers.organization.VirtualCollections.list(orgId)
  GET         /organizations/:orgId/virtualCollection/add                       controllers.organization.VirtualCollections.virtualCollection(orgId: String, spec: Option[String] = None)
  GET         /organizations/:orgId/virtualCollection/:spec/update              controllers.organization.VirtualCollections.virtualCollection(orgId: String, spec: Option[String])
  POST        /organizations/:orgId/virtualCollection/submit                    controllers.organization.VirtualCollections.submit(orgId: String)
  GET         /organizations/:orgId/virtualCollection/:spec                     controllers.organization.VirtualCollections.view(orgId, spec)
  DELETE      /organizations/:orgId/virtualCollection/:spec                     controllers.organization.VirtualCollections.delete(orgId, spec)
  */

  override val routes: ListMap[(String, Regex), (List[String]) => Handler] = ListMap(
    ("GET", """^/organizations/([A-Za-z0-9-]+)/virtualCollection$""".r) -> {
      pathArgs: List[String] => controllers.organization.VirtualCollections.list(pathArgs(0))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/virtualCollection/add$""".r) -> {
      pathArgs: List[String] => controllers.organization.VirtualCollections.virtualCollection(pathArgs(0), None)
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/virtualCollection/([A-Za-z0-9-]+)/update$""".r) -> {
      pathArgs: List[String] => controllers.organization.VirtualCollections.virtualCollection(pathArgs(0), Some(pathArgs(1)))
    },
    ("POST", """^/organizations/([A-Za-z0-9-]+)/virtualCollection/submit$""".r) -> {
      pathArgs: List[String] => controllers.organization.VirtualCollections.submit(pathArgs(0))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/virtualCollection/([A-Za-z0-9-]+)""".r) -> {
      pathArgs: List[String] => controllers.organization.VirtualCollections.view(pathArgs(0), pathArgs(1))
    },
    ("DELETE", """^/organizations/([A-Za-z0-9-]+)/virtualCollection/([A-Za-z0-9-]+)""".r) -> {
      pathArgs: List[String] => controllers.organization.VirtualCollections.delete(pathArgs(0), pathArgs(1))
    }
  )


  override def organizationMenuEntries(context: Map[String, String], roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "virtual-collections",
      titleKey = "thing.virtualCollections",
      items = Seq(
        MenuElement("/organizations/%s/virtualCollection".format(context("orgId")), "org.vc.list"),
        MenuElement("/organizations/%s/virtualCollection/add".format(context("orgId")), "org.vc.new", Seq(GrantType.OWN))
      )
    )
  )

  override def getHarvestCollectionLookups: Seq[HarvestCollectionLookup] = Seq(customHarvestCollectionHarvestCollectionLookup)

}
