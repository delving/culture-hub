package plugins

import play.api.{Configuration, Application}
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
        controllers.organization.CMS.list(pathArgs(0), None, Some(CMSPlugin.MAIN_MENU))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.list(pathArgs(0), Some(pathArgs(1)), Some(pathArgs(2)))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site/([A-Za-z0-9-]+)/page/add""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.page(orgId = pathArgs(0), language = pathArgs(1), page = None, menu = CMSPlugin.MAIN_MENU)
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site/([A-Za-z0-9-]+)/page/add/([A-Za-z0-9-]+)""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.page(orgId = pathArgs(0), language = pathArgs(1), page = None, menu = pathArgs(2))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/site/([A-Za-z0-9-]+)/page/([A-Za-z0-9-]+)/update""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.organization.CMS.page(orgId = pathArgs(0), language = pathArgs(1), page = Some(pathArgs(2)), menu = CMSPlugin.MAIN_MENU)
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

        val mainMenu = Menu("mainMenu", None, Lang.availables.map(lang => (lang.code -> Messages("plugin.cms.mainMenu")(lang))).toMap)
        val homePage = Menu("homePage", None, Lang.availables.map(lang => (lang.code -> Messages("plugin.cms.homePage")(lang))).toMap)

        CMSPluginConfiguration(Seq(mainMenu, homePage) ++ menus)
      }
    }
  }

  override def onStart() {


    DomainConfigurationHandler.domainConfigurations.foreach { implicit configuration =>

      // make sure that all the menu definitions in the configuration have an up-to-date menu entry for their parent menu
      // TODO sync removed entries
      cmsPluginConfiguration.get(configuration).map { config =>
        config.
          menuDefinitions.
          filterNot(_.parentMenuKey == None).
          zipWithIndex.foreach { definition =>
          MenuEntry.dao.findOneByMenuKeyAndTargetMenuKey(definition._1.parentMenuKey.get, definition._1.key).map { persisted =>
            val updated = persisted.copy(
              position = definition._2,
              title = definition._1.title,
              targetMenuKey = Some(definition._1.key),
              targetPageKey = None,
              targetUrl = None
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

      // create empty homepage for all languages
      Lang.availables.foreach { lang =>
        if (CMSPage.dao.findByKeyAndLanguage("homepage", lang.code).isEmpty) {
          val homePage = CMSPage(
            key = "homepage",
            userName = "system",
            orgId = configuration.orgId,
            lang = lang.code,
            title = "Homepage",
            content = ""
          )
          CMSPage.dao.insert(homePage)
        }
      }

      if (MenuEntry.dao.findOneByKey(CMSPlugin.HOME_PAGE).isEmpty) {
        val homePageEntry = MenuEntry(
          orgId = configuration.orgId,
          menuKey = CMSPlugin.HOME_PAGE,
          parentMenuKey = None,
          position = 0,
          title = Lang.availables.map(lang => (lang.code -> Messages("plugin.cms.homePage")(lang))).toMap,
          targetPageKey = Some("homepage"),
          published = false
        )
        MenuEntry.dao.insert(homePageEntry)
      }

    }
  }

  override def mainMenuEntries(configuration: DomainConfiguration, lang: String): Seq[MainMenuEntry] = {
    def isVisible(entry: MenuEntry) = entry.title.contains(lang) && entry.published
    models.cms.MenuEntry.dao(configuration).
      findEntries(configuration.orgId, CMSPlugin.MAIN_MENU).
      filter(isVisible).
      filterNot(e => e.targetMenuKey.isDefined && models.cms.MenuEntry.dao(configuration).findEntries(e.orgId, e.targetMenuKey.get).filter(isVisible).isEmpty).
      map { e =>

          val targetUrl = if (e.targetPageKey.isDefined && e.menuKey != CMSPlugin.MAIN_MENU) {
            "/site" + e.menuKey + "/page/" + e.targetPageKey.get
          } else if(e.targetPageKey.isDefined && e.menuKey == CMSPlugin.MAIN_MENU) {
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

  override def organizationMenuEntries(configuration: DomainConfiguration, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = {
    
    cmsPluginConfiguration.get(configuration).map { config =>
      config.menuDefinitions.filterNot(_.key == CMSPlugin.HOME_PAGE).map { definition =>

        if (definition.key == CMSPlugin.MAIN_MENU) {
          // default menu for site pages
          MainMenuEntry(
            key = CMSPlugin.MAIN_MENU,
            titleKey = "plugin.cms",
            roles = Seq(Role.OWN, CMSPlugin.ROLE_CMS_ADMIN),
            items = Seq(
              MenuElement("/organizations/%s/site/%s/%s".format(configuration.orgId, lang, CMSPlugin.MAIN_MENU), "ui.label.list"),
              MenuElement("/organizations/%s/site/%s/page/add".format(configuration.orgId, lang), "ui.label.new"),
              MenuElement("/organizations/%s/site/%s/page/homepage/update".format(configuration.orgId, lang), "plugin.cms.updateHomePage"),
              MenuElement("/organizations/%s/site/upload".format(configuration.orgId), "plugin.cms.upload.image")
            )
          )
        } else {
          MainMenuEntry(
            key = definition.key,
            titleKey = definition.title.get(lang).getOrElse(definition.title("en")),
            roles = Seq(Role.OWN, CMSPlugin.ROLE_CMS_ADMIN),
            items = Seq(
              MenuElement("/organizations/%s/site/%s/%s".format(configuration.orgId, lang, definition.key), "ui.label.list"),
              MenuElement("/organizations/%s/site/%s/page/add/%s".format(configuration.orgId, lang, definition.key), "ui.label.new"),
              MenuElement("/organizations/%s/site/upload".format(configuration.orgId), "plugin.cms.upload.image")
            )
          )
        }
      }
    }.getOrElse {
      Seq.empty
    }

  } 

  override def homePageSnippet: Option[(String, RequestContext => Unit)] = Some(
    ("/CMS/homePageSnippet.html",
    { context => {
        val homePageEntries = CMSPage.dao(context.configuration).list(context.configuration.orgId, context.lang, Some(CMSPlugin.HOME_PAGE))
        homePageEntries.headOption.map { page =>
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

  val MAIN_MENU = "mainMenu"
  val HOME_PAGE = "homePage"

  def getConfiguration(implicit configuration: DomainConfiguration): Option[CMSPluginConfiguration] = {
    CultureHubPlugin.getPlugin(classOf[CMSPlugin]).flatMap(_.cmsPluginConfiguration.get(configuration))
  }

}

case class CMSPluginConfiguration(menuDefinitions: Seq[Menu])