package plugins

import play.api.Application
import core.CultureHubPlugin
import models._
import core.MainMenuEntry
import core.MenuElement
import core.search.BasicSearchInService
import controllers.api.IndexItemOrganizationCollectionLookupService

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CorePlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "core"

  override def enabled: Boolean = true

  override def isEnabled(configuration: OrganizationConfiguration): Boolean = true

  override def mainMenuEntries(configuration: OrganizationConfiguration, lang: String): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "home",
      titleKey = "hub.Home",
      mainEntry = Some(MenuElement(url = "/", titleKey = "hub.Home"))
    )
  )

  override def services: Seq[Any] = Seq(
    new BasicSearchInService
  )

  override def organizationMenuEntries(configuration: OrganizationConfiguration, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "overview",
      titleKey = "hub.Overview",
      mainEntry = Some(MenuElement("/admin", "hub.Overview")),
      membersOnly = false
    ),
    MainMenuEntry(
      key = "administration",
      titleKey = "hub.Administration",
      mainEntry = Some(MenuElement("/admin/admin", "hub.Administration")),
      roles = Seq(Role.OWN)
    ),
    MainMenuEntry(
      key = "groups",
      titleKey = "hubb.Groups",
      items = Seq(
        MenuElement("/admin/groups", "hub.GroupList"),
        MenuElement("/admin/groups/create", "hub.CreateGroup", Seq(Role.OWN))
      )
    )
  )

}