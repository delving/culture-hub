package plugins

import play.api.{Configuration, Application}
import controllers.organization.CMSPageViewModel
import core._
import models.{DomainConfiguration, Role}
import models.cms.{MenuEntry, Menu, CMSPage}
import com.mongodb.casbah.Imports._
import core.MainMenuEntry
import core.MenuElement
import scala.util.matching.Regex
import play.api.mvc.Handler
import scala.collection.immutable.ListMap
import util.DomainConfigurationHandler
import play.api.i18n.{Messages, Lang}
import play.api.Play.current

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CMSPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = CMSPlugin.PLUGIN_KEY

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
        controllers.CMS.page(pathArgs(0), None)
    },
    ("GET", """^/site/([A-Za-z0-9-]+)/page/([A-Za-z0-9-]+)""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.CMS.page(pathArgs(1), Some(pathArgs(0)))
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
        controllers.organization.CMS.page(orgId = pathArgs(0), language = pathArgs(1), page = None, menu = CMSPageViewModel.NO_MENU)
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site/([A-Za-z0-9-]+)/page/([A-Za-z0-9-]+)/update""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.page(orgId = pathArgs(0), language = pathArgs(1), page = Some(pathArgs(2)), menu = CMSPageViewModel.NO_MENU)
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

  private var cmsPluginConfiguration: Map[DomainConfiguration, CMSPluginConfiguration] = Map.empty

  def getPluginConfiguration(implicit configuration: DomainConfiguration) = cmsPluginConfiguration(configuration)

  override def onBuildConfiguration(configurations: Map[DomainConfiguration, Option[Configuration]]) {
    cmsPluginConfiguration = configurations.map { pair =>
      pair._1 -> {
        val menus: Seq[Menu] = pair._2.flatMap { c =>
          c.getConfig("menuDefinitions").map { menuDefinitionsConfig =>
            val menuDefinitions = menuDefinitionsConfig.keys.filterNot(_.indexOf(".") < 0).map(_.split("\\.").head).toSeq.distinct

            menuDefinitions.map { menuDefinitionKey =>
              val menuDefinition = menuDefinitionsConfig.getConfig(menuDefinitionKey).get
              val parentMenuKey = menuDefinition.getString("parentMenuKey")
              val title: Option[Map[String, String]] = menuDefinition.getConfig("title").map { titleConfig =>
                titleConfig.keys.toSeq.map { lang =>
                  (lang -> titleConfig.getString(lang).getOrElse(""))
                }.toMap
              }

              if (parentMenuKey == None || title == None) {
                throw new RuntimeException("Invalid CMS configuration for menu '%s': missing either 'parentMenuKey' or 'title'".format(menuDefinitionKey))
              }

              Menu(key = menuDefinitionKey, parentMenuKey = parentMenuKey, title = title.get)
            }
          }
        }.getOrElse {
          Seq.empty
        }

        CMSPluginConfiguration(Seq(Menu(
          "mainMenu",
          None,
          Lang.availables.map(lang => (lang.code -> Messages("plugin.cms.mainMenu")(lang))).toMap
        )) ++ menus)
      }
    }
  }

  override def onStart() {

    // make sure that all the menu definitions in the configuration have an up-to-date menu entry for their parent menu

    // TODO sync removed entries
    DomainConfigurationHandler.domainConfigurations.foreach { implicit configuration =>
      getPluginConfiguration(configuration).
        menuDefinitions.
        filterNot(_.parentMenuKey == None).
        zipWithIndex.foreach { definition =>
        MenuEntry.dao.findOneByMenuKey(definition._1.parentMenuKey.get).map { persisted =>
          val updated = persisted.copy(
            position = definition._2,
            title = definition._1.title,
            targetMenuKey = Some(definition._1.key)
          )
          MenuEntry.dao.save(updated)
        }.getOrElse {
          val entry = MenuEntry(
            orgId = configuration.orgId,
            menuKey = definition._1.parentMenuKey.get,
            position = definition._2,
            title = definition._1.title,
            targetMenuKey = Some(definition._1.key),
            published = true
          )
          MenuEntry.dao.insert(entry)
        }
      }
    }

  }

  override def mainMenuEntries(configuration: DomainConfiguration, lang: String): Seq[MainMenuEntry] = {
    models.cms.MenuEntry.dao(configuration).
      findEntries(configuration.orgId, CMSPageViewModel.MAIN_MENU).
      filterNot(e => !e.title.contains(lang) || !e.published).
      map { e =>

          val targetUrl = if (e.targetPageKey.isDefined && e.menuKey != CMSPageViewModel.MAIN_MENU) {
            "/site" + e.menuKey + "/page/" + e.targetPageKey.get
          } else if(e.targetPageKey.isDefined && e.menuKey == CMSPageViewModel.MAIN_MENU) {
            "/page/" + e.targetPageKey.get
          } else if (e.targetMenuKey.isDefined) {
            val first = MenuEntry.dao(configuration).findEntries(configuration.orgId, e.targetMenuKey.get).toSeq.headOption
            "/site/" + e.targetMenuKey.get + "/page/" + first.flatMap(_.targetPageKey).getOrElse("")
          } else if (e.targetUrl.isDefined) {
            e.targetUrl.get
          } else {
            ""
          }

          MainMenuEntry(
            key = e.menuKey,
            titleKey = e.title(lang),
            mainEntry = Some(MenuElement(url = targetUrl, titleKey = e.title(lang)))
          )
      }.toSeq
  }

  override def organizationMenuEntries(configuration: DomainConfiguration, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "site",
      titleKey = "plugin.cms",
      roles = Seq(Role.OWN, CMSPlugin.ROLE_CMS_ADMIN),
      items = Seq(
        MenuElement("/organizations/%s/site".format(configuration.orgId), "ui.label.list"),
        MenuElement("/organizations/%s/site/%s/page/add".format(configuration.orgId, lang), "ui.label.new"),
        MenuElement("/organizations/%s/site/upload".format(configuration.orgId), "plugin.cms.upload.image")
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

  override def roles: Seq[Role] = Seq(CMSPlugin.ROLE_CMS_ADMIN)
}

object CMSPlugin {

  val PLUGIN_KEY = "cms"

  lazy val ROLE_CMS_ADMIN = Role("cms", Role.descriptions("plugin.cms.adminRight"), false, None)

  def getConfiguration(implicit configuration: DomainConfiguration): Option[CMSPluginConfiguration] = {
    CultureHubPlugin.getPlugin(classOf[CMSPlugin]).map(_.getPluginConfiguration)
  }

}

case class CMSPluginConfiguration(menuDefinitions: Seq[Menu])