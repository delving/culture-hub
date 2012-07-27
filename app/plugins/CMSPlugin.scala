package plugins

import play.api.Application
import controllers.organization.CMS
import core.{HubServices, MenuElement, MainMenuEntry, CultureHubPlugin}
import models.{DomainConfiguration, GrantType}
import models.cms.{MenuEntry, CMSPage}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CMSPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "cms"


  override def onApplicationStart() {
    CMSPage.init(HubServices.configurations)
    MenuEntry.init(HubServices.configurations)
  }

  override def mainMenuEntries(implicit configuration: DomainConfiguration, lang: String): Seq[MainMenuEntry] = {
    _root_.models.cms.MenuEntry.dao.findEntries(configuration.name, CMS.MAIN_MENU).filterNot(e => !e.title.contains(lang) || !e.published).map(e => MainMenuEntry(
      key = e.menuKey,
      titleKey = e.title(lang),
      mainEntry = Some(MenuElement(url = "/page/" + e.targetPageKey.getOrElse(""), titleKey = e.title(lang)))
    )).toSeq
  }

  override def organizationMenuEntries(context: Map[String, String], roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "site",
      titleKey = "org.cms",
      roles = Seq(GrantType.OWN, GrantType.CMS),
      items = Seq(
        MenuElement("/organizations/%s/site".format(context("orgId")), "org.cms.page.list"),
        MenuElement("/organizations/%s/site/%s/page/add".format(context("orgId"), context("currentLanguage")), "org.cms.page.new"),
        MenuElement("/organizations/%s/site/upload".format(context("orgId")), "org.cms.upload.image")
      )
    )
  )
}
