package plugins

import play.api.Application
import controllers.organization.CMS
import core.{HubServices, MenuElement, MainMenuEntry, CultureHubPlugin}
import models.{DomainConfiguration, Role}
import models.cms.{MenuEntry, CMSPage}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CMSPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "cms"

  override def mainMenuEntries(implicit configuration: DomainConfiguration, lang: String): Seq[MainMenuEntry] = {
    _root_.models.cms.MenuEntry.dao.
            findEntries(configuration.orgId, CMS.MAIN_MENU).
            filterNot(e => !e.title.contains(lang) || !e.published).
            filterNot(e => _root_.models.cms.CMSPage.dao.findByKeyAndLanguage(e.targetPageKey.getOrElse(""), lang).isEmpty).
            map(e =>
                    MainMenuEntry(
                       key = e.menuKey,
                       titleKey = e.title(lang),
                       mainEntry = Some(MenuElement(url = "/page/" + e.targetPageKey.getOrElse(""), titleKey = e.title(lang)))
                    )
            ).toSeq
  }

  override def organizationMenuEntries(orgId: String, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "site",
      titleKey = "org.cms",
      roles = Seq(Role.OWN, Role.CMS),
      items = Seq(
        MenuElement("/organizations/%s/site".format(orgId), "org.cms.page.list"),
        MenuElement("/organizations/%s/site/%s/page/add".format(orgId, lang), "org.cms.page.new"),
        MenuElement("/organizations/%s/site/upload".format(orgId), "org.cms.upload.image")
      )
    )
  )
}
