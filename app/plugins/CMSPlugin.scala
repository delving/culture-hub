package plugins

import play.api.Application
import controllers.organization.CMS
import core.{RequestContext, CultureHubPlugin}
import models.MenuEntry

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CMSPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "cms"

  override val onApplicationRequest: RequestContext => Unit = {
    context =>

      // Menu entries
      val mainMenuEntries = MenuEntry.findEntries(context.theme.name, CMS.MAIN_MENU).filterNot(!_.title.contains(context.lang)).map(e => (Map(
        "title" -> e.title(context.lang),
        "page" -> e.targetPageKey.getOrElse(""),
        "published" -> e.published))
      ).toList
      context.renderArgs.put("menu", mainMenuEntries)
  }
}
