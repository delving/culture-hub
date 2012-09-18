package plugins

import play.api.Application
import controllers.organization.CMS
import core._
import models.{DomainConfiguration, Role}
import scala.collection.mutable
import models.cms.CMSPage
import com.mongodb.casbah.Imports._
import scala.Some
import scala.collection
import core.MainMenuEntry
import scala.Some
import core.MenuElement

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
      roles = Seq(Role.OWN, CMSPlugin.ROLE_CMS_ADMIN),
      items = Seq(
        MenuElement("/organizations/%s/site".format(orgId), "org.cms.page.list"),
        MenuElement("/organizations/%s/site/%s/page/add".format(orgId, lang), "org.cms.page.new"),
        MenuElement("/organizations/%s/site/upload".format(orgId), "org.cms.upload.image")
      )
    )
  )


  override def homePageSnippet: Option[(String, RequestContext => Unit)] = Some(
    ("/CMS/homePageSnippet.html",
    { context => {
        CMSPage.dao(context.configuration).find(
          MongoDBObject("key" -> "homepage", "lang" -> context.lang, "orgId" -> context.configuration.orgId)
        ).$orderby(MongoDBObject("_id" -> -1)).
          limit(1).
          toList.
          headOption.foreach { page =>
          context.renderArgs += ("homepageCmsContent" -> page)
        }
      }
    })
  )

  /**
   * Override this to provide custom roles to the platform, that can be used in Groups
   * @return a sequence of [[models.Role]] instances
   */
  override def roles: Seq[Role] = Seq(CMSPlugin.ROLE_CMS_ADMIN)
}

object CMSPlugin {
  val ROLE_CMS_ADMIN = Role("cms", Role.descriptions("plugin.cms.adminRight"), false, None)
}