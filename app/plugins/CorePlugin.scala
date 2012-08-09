package plugins

import play.api.Application
import core.{HubServices, MenuElement, MainMenuEntry, CultureHubPlugin}
import models._
import core.collection.HarvestCollectionLookup
import core.MainMenuEntry
import scala.Some
import core.MenuElement


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CorePlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "core"

  private val dataSetHarvestCollectionLookup = new DataSetHarvestCollectionLookup

  override def enabled: Boolean = true


  override def isEnabled(configuration: DomainConfiguration): Boolean = true

  override def mainMenuEntries(implicit configuration: DomainConfiguration, lang: String): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "home",
      titleKey = "site.nav.home",
      mainEntry = Some(MenuElement(url = "/", titleKey = "site.nav.home"))
    )
  )

  override def organizationMenuEntries(orgId: String, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "overview",
      titleKey = "ui.label.overview",
      mainEntry = Some(MenuElement("/organizations/" + orgId, "ui.label.overview")),
      membersOnly = false
    ),
    MainMenuEntry(
      key = "administration",
      titleKey = "ui.label.administration",
      mainEntry = Some(MenuElement("/organizations/%s/admin".format(orgId), "ui.label.administration")),
      roles = Seq(Role.OWN)
    ),
    MainMenuEntry(
      key = "groups",
      titleKey = "thing.groups",
      items = Seq(
        MenuElement("/organizations/%s/groups".format(orgId), "org.group.list"),
        MenuElement("/organizations/%s/groups/create".format(orgId), "org.group.create", Seq(Role.OWN))
      )
    ),
    MainMenuEntry(
      key = "datasets",
      titleKey = "thing.datasets",
      items = Seq(
        MenuElement("/organizations/%s/dataset".format(orgId), "organization.dataset.list"),
        MenuElement("/organizations/%s/dataset/add".format(orgId), "organization.dataset.create", Seq(Role.OWN))
      )
    ),
    MainMenuEntry(
      key = "sipcreator",
      titleKey = "ui.label.sipcreator",
      mainEntry = Some(MenuElement("/organizations/%s/sip-creator".format(orgId), "ui.label.sipcreator"))
    )
  )

  override def harvestCollectionLookups: Seq[HarvestCollectionLookup] = Seq(dataSetHarvestCollectionLookup)
}