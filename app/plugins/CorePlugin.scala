package plugins

import play.api.Application
import core.CultureHubPlugin
import models._
import core.MainMenuEntry
import core.MenuElement
import core.search.BasicSearchInService


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CorePlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "core"

  override def enabled: Boolean = true

  override def isEnabled(configuration: DomainConfiguration): Boolean = true

  override def mainMenuEntries(implicit configuration: DomainConfiguration, lang: String): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "home",
      titleKey = "site.nav.home",
      mainEntry = Some(MenuElement(url = "/", titleKey = "site.nav.home"))
    )
  )


  override def services: Seq[Any] = Seq(
    new BasicSearchInService
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
    )
  )


}