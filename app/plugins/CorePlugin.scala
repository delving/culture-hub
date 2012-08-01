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

  override def mainMenuEntries(implicit configuration: DomainConfiguration, lang: String): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "home",
      titleKey = "site.nav.home",
      mainEntry = Some(MenuElement(url = "/", titleKey = "site.nav.home"))
    )
  )

  override def organizationMenuEntries(context: Map[String, String], roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "overview",
      titleKey = "ui.label.overview",
      mainEntry = Some(MenuElement("/organizations/" + context("orgId"), "ui.label.overview")),
      membersOnly = false
    ),
    MainMenuEntry(
      key = "administration",
      titleKey = "ui.label.administration",
      mainEntry = Some(MenuElement("/organizations/%s/admin".format(context("orgId")), "ui.label.administration")),
      roles = Seq(GrantType.OWN)
    ),
    MainMenuEntry(
      key = "groups",
      titleKey = "thing.groups",
      items = Seq(
        MenuElement("/organizations/%s/groups".format(context("orgId")), "org.group.list"),
        MenuElement("/organizations/%s/groups/create".format(context("orgId")), "org.group.create", Seq(GrantType.OWN))
      )
    ),
    MainMenuEntry(
      key = "datasets",
      titleKey = "thing.datasets",
      items = Seq(
        MenuElement("/organizations/%s/dataset".format(context("orgId")), "organization.dataset.list"),
        MenuElement("/organizations/%s/dataset/add".format(context("orgId")), "organization.dataset.create", Seq(GrantType.OWN))
      )
    ),
    MainMenuEntry(
      key = "sipcreator",
      titleKey = "ui.label.sipcreator",
      mainEntry = Some(MenuElement("/organizations/%s/sip-creator".format(context("orgId")), "ui.label.sipcreator"))
    )
  )

  override def getHarvestCollectionLookups: Seq[HarvestCollectionLookup] = Seq(dataSetHarvestCollectionLookup)
}