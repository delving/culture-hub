package plugins

import play.api.Application
import controllers.organization.CMS
import core.{MenuElement, MainMenuEntry, RequestContext, CultureHubPlugin}
import models.GrantType

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CMSPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "cms"

  override val onApplicationRequest: RequestContext => Unit = {
    context =>
    // Add menu entries to main application
      val mainMenuEntries = models.cms.MenuEntry.findEntries(context.theme.name, CMS.MAIN_MENU).filterNot(!_.title.contains(context.lang)).map(e => (Map(
        "title" -> e.title(context.lang),
        "page" -> e.targetPageKey.getOrElse(""),
        "published" -> e.published))
      ).toList
      context.renderArgs.put("menu", mainMenuEntries)
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
