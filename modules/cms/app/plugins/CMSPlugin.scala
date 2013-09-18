package plugins

import play.api.{ Configuration, Application }
import core._
import models.{ OrganizationConfiguration, Role }
import models.cms.{ MenuEntry, Menu, CMSPage }
import com.mongodb.casbah.Imports._
import core.MainMenuEntry
import core.MenuElement
import scala.util.matching.Regex
import play.api.mvc.Handler
import scala.collection.immutable.ListMap
import util.{ OrganizationConfigurationResourceHolder, OrganizationConfigurationHandler }
import play.api.i18n.{ Messages, Lang }
import play.api.Play.current

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CMSPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = CMSPlugin.PLUGIN_KEY

  private var cmsPluginConfiguration: Map[OrganizationConfiguration, CMSPluginConfiguration] = Map.empty

  override def onBuildConfiguration(configurations: Map[OrganizationConfiguration, Option[Configuration]]) {
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

        val mainMenu = Menu("mainMenu", None, Lang.availables.map(lang => (lang.code -> Messages("cms.MainMenu")(lang))).toMap)
        val homePage = Menu("homePage", None, Lang.availables.map(lang => (lang.code -> Messages("cms.Homepage")(lang))).toMap)

        CMSPluginConfiguration(Seq(mainMenu, homePage) ++ menus)
      }
    }
  }

  lazy val cmsMenus = new OrganizationConfigurationResourceHolder[OrganizationConfiguration, MenuEntry]("cmsMenus") {

    protected def resourceConfiguration(configuration: OrganizationConfiguration): OrganizationConfiguration = configuration

    protected def onAdd(resourceConfiguration: OrganizationConfiguration): Option[MenuEntry] = {
      implicit val configuration = resourceConfiguration

      // make sure that all the menu definitions in the configuration have an up-to-date menu entry for their parent menu
      // TODO sync removed entries
      cmsPluginConfiguration.get(resourceConfiguration).map { config =>
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
            lang = lang.code,
            title = "Homepage",
            content = ""
          )
          CMSPage.dao.insert(homePage)
        }
      }

      if (MenuEntry.dao.findOneByKey(CMSPlugin.HOME_PAGE).isEmpty) {
        val homePageEntry = MenuEntry(
          menuKey = CMSPlugin.HOME_PAGE,
          parentMenuKey = None,
          position = 0,
          title = Lang.availables.map(lang => (lang.code -> Messages("cms.Homepage")(lang))).toMap,
          targetPageKey = Some("homepage"),
          published = false
        )
        MenuEntry.dao.insert(homePageEntry)
      }

      // TODO this makes no sense, the whole case here is just to be part of the lifecycle and not to return a value
      MenuEntry.dao.findOneByKey(CMSPlugin.HOME_PAGE)

    }

    protected def onRemove(removed: MenuEntry) {}
  }

  override def onStart() {
    OrganizationConfigurationHandler.registerResourceHolder(cmsMenus)
  }

  override def mainMenuEntries(configuration: OrganizationConfiguration, lang: String): Seq[MainMenuEntry] = {
    def isVisible(entry: MenuEntry) = entry.title.contains(lang) && entry.published
    models.cms.MenuEntry.dao(configuration.orgId).
      findEntries(CMSPlugin.MAIN_MENU).
      filter(isVisible).
      filterNot(e => e.targetMenuKey.isDefined && models.cms.MenuEntry.dao(configuration.orgId).findEntries(e.targetMenuKey.get).filter(isVisible).isEmpty).
      map { e =>

        val targetUrl = if (e.targetPageKey.isDefined && e.menuKey != CMSPlugin.MAIN_MENU) {
          "/site" + e.menuKey + "/page/" + e.targetPageKey.get
        } else if (e.targetPageKey.isDefined && e.menuKey == CMSPlugin.MAIN_MENU) {
          "/page/" + e.targetPageKey.get
        } else if (e.targetMenuKey.isDefined) {
          val first = MenuEntry.dao(configuration.orgId).findEntries(e.targetMenuKey.get).toSeq.headOption
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

  override def organizationMenuEntries(configuration: OrganizationConfiguration, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = {

    cmsPluginConfiguration.get(configuration).map { config =>
      config.menuDefinitions.filterNot(_.key == CMSPlugin.HOME_PAGE).map { definition =>

        if (definition.key == CMSPlugin.MAIN_MENU) {
          // default menu for site pages
          MainMenuEntry(
            key = CMSPlugin.MAIN_MENU,
            titleKey = "cms.WebsitePages",
            roles = Seq(Role.OWN, CMSPlugin.ROLE_CMS_ADMIN),
            items = Seq(
              MenuElement("/admin/site/%s/%s".format(lang, CMSPlugin.MAIN_MENU), "hub.List"),
              MenuElement("/admin/site/%s/page/add".format(lang), "hub.New"),
              MenuElement("/admin/site/%s/page/homepage/update".format(lang), "cms.UpdateHomepage"),
              MenuElement("/admin/site/upload".format(configuration.orgId), "cms.UploadImage")
            )
          )
        } else {
          MainMenuEntry(
            key = definition.key,
            titleKey = definition.title.get(lang).getOrElse(definition.title("en")),
            roles = Seq(Role.OWN, CMSPlugin.ROLE_CMS_ADMIN),
            items = Seq(
              MenuElement("/admin/site/%s/%s".format(lang, definition.key), "hub.List"),
              MenuElement("/admin/site/%s/page/add/%s".format(lang, definition.key), "hub.New"),
              MenuElement("/admin/site/upload", "cms.UploadImage")
            )
          )
        }
      }
    }.getOrElse {
      Seq.empty
    }

  }

  override def homePageSnippet: Option[(String, RequestContext => Unit)] = Some(
    ("/cms/CMS/homePageSnippet.html",
      { context =>
        {
          val homePageEntries = CMSPage.dao(context.configuration.orgId).entryList(Lang(context.lang), Some(CMSPlugin.HOME_PAGE))
          homePageEntries.headOption.map { entry =>
            context.renderArgs += ("homepageCmsContent" -> entry.page)
          }
        }
      })
  )

  override def roles: Seq[Role] = Seq(CMSPlugin.ROLE_CMS_ADMIN)
}

object CMSPlugin {

  val PLUGIN_KEY = "cms"

  lazy val ROLE_CMS_ADMIN = Role("cms", Role.descriptions("cms.SiteContentAdministrationRights"), false, None)

  val MAIN_MENU = "mainMenu"
  val HOME_PAGE = "homePage"

  def getConfiguration(implicit configuration: OrganizationConfiguration): Option[CMSPluginConfiguration] = {
    CultureHubPlugin.getPlugin(classOf[CMSPlugin]).flatMap(_.cmsPluginConfiguration.get(configuration))
  }

}

case class CMSPluginConfiguration(menuDefinitions: Seq[Menu])