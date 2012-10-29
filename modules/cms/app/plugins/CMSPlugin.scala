package plugins

import play.api.Application
import controllers.organization.CMS
import core._
import models.{DomainConfiguration, Role}
import models.cms.CMSPage
import com.mongodb.casbah.Imports._
import core.MainMenuEntry
import core.MenuElement
import scala.util.matching.Regex
import play.api.mvc.Handler
import scala.collection.immutable.ListMap

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CMSPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "cms"

  /**

   GET        /page/:key                                                         controllers.Application.page(key)

   GET         /organizations/:orgId/site/upload                                 controllers.organization.CMS.upload(orgId)
   POST        /organizations/:orgId/site/upload/:uid                            controllers.organization.CMS.uploadSubmit(orgId, uid)
   GET         /organizations/:orgId/site/listImages                             controllers.organization.CMS.listImages(orgId)
   GET         /organizations/:orgId/site                                        controllers.organization.CMS.list(orgId, language: Option[String] = None)
   GET         /organizations/:orgId/site/:language                              controllers.organization.CMS.list(orgId, language: Option[String])
   GET         /organizations/:orgId/site/:language/page/add                     controllers.organization.CMS.page(orgId, language, page: Option[String] = None)
   GET         /organizations/:orgId/site/:language/page/:page/update            controllers.organization.CMS.page(orgId, language, page: Option[String])
   POST        /organizations/:orgId/site/page                                   controllers.organization.CMS.pageSubmit(orgId)
   DELETE      /organizations/:orgId/site/:language/page/:key                    controllers.organization.CMS.pageDelete(orgId, key, language)
   GET         /organizations/:orgId/site/:language/page/preview/:key            controllers.organization.CMS.pagePreview(orgId, language, key)

   */
  override val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap(

    ("GET", """^/page/([A-Za-z0-9-]+)""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.CMS.page(pathArgs(0))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site/upload""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.upload(pathArgs(0))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site/upload""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.upload(pathArgs(0))
    },
    ("POST", """^/organizations/([A-Za-z0-9-]+)/site/upload/([A-Za-z0-9-]+)""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.uploadSubmit(pathArgs(0), pathArgs(1))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site/listImages""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.listImages(pathArgs(0))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.list(pathArgs(0), None)
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site/([A-Za-z0-9-]+)""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.list(pathArgs(0), Some(pathArgs(1)))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site/([A-Za-z0-9-]+)/page/add""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.page(pathArgs(0), pathArgs(1), None)
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site/([A-Za-z0-9-]+)/page/([A-Za-z0-9-]+)/update""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.page(pathArgs(0), pathArgs(1), Some(pathArgs(2)))
    },
    ("POST", """^/organizations/([A-Za-z0-9-]+)/site/page""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.pageSubmit(pathArgs(0))
    },
    ("DELETE", """^/organizations/([A-Za-z0-9-]+)/site/([A-Za-z0-9-]+)/page/([A-Za-z0-9-]+)""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.pageDelete(pathArgs(0), pathArgs(2), pathArgs(1))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site/([A-Za-z0-9-]+)/page/([A-Za-z0-9-]+)/preview""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.pagePreview(pathArgs(0), pathArgs(1), pathArgs(2))
    }

  )

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
  lazy val ROLE_CMS_ADMIN = Role("cms", Role.descriptions("plugin.cms.adminRight"), false, None)
}