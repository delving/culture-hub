package controllers.cms.organization

import play.api.mvc._
import play.api.i18n._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import extensions.Formatters._
import controllers.{ BoundController, ViewModel, OrganizationController }
import extensions.{ MissingLibs, JJson }
import models._
import cms.{ MenuEntry, CMSPage }
import com.mongodb.casbah.Imports._
import core.HubModule
import plugins.CMSPlugin
import scala.collection.JavaConverters._
import core.storage.{ FileUploadResponse, FileStorage }
import controllers.dos.FileUpload

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object CMS extends BoundController(HubModule) with CMS

trait CMS extends OrganizationController { this: BoundController =>

  def CMSAction[A](action: Action[A]): Action[A] = {
    OrganizationMember {
      Action(action.parser) {
        implicit request =>
          {
            if (organizationServiceLocator.byDomain.isAdmin(configuration.orgId, connectedUser) || Group.dao.count(MongoDBObject("users" -> connectedUser, "grantType" -> CMSPlugin.ROLE_CMS_ADMIN.key)) > 0) {
              action(request)
            } else {
              Forbidden(Messages("hub.YouDoNotHaveAccess"))
            }
          }
      }
    }
  }

  def list(language: Option[String], menu: Option[String]) = CMSAction {
    Action {
      implicit request =>
        val lang = language.getOrElse(getLang)
        val pages = CMSPage.dao.list(lang, menu)
        Ok(Template('data -> JJson.generate(Map("pages" -> pages)), 'languages -> getLanguages, 'currentLanguage -> lang, 'menuKey -> menu.getOrElse("")))
    }
  }

  def upload = CMSAction {
    Action {
      implicit request =>
        val files = FileStorage.listFiles(configuration.orgId).map(f => FileUploadResponse(f))
        Ok(Template('uid -> MissingLibs.UUID, 'files -> JJson.generate(files)))
    }
  }

  def uploadSubmit(uid: String) = CMSAction {
    Action {
      implicit request =>
        FileUpload.markFilesAttached(uid, configuration.orgId)
        Redirect(routes.CMS.upload())
    }
  }

  def listImages = CMSAction {
    Action {
      implicit request =>
        val images = FileStorage.listFiles(configuration.orgId).filter(_.contentType.contains("image"))

        // tinyMCE stoopidity
        val javascript = "var tinyMCEImageList = new Array(" + images.map(i => """["%s","%s"]""".format(i.name, "/file/image/%s".format(i.id))).mkString(", ") + ");"
        Ok(javascript).as("text/html")
    }
  }

  def page(language: String, page: Option[String], menu: String): Action[AnyContent] = CMSAction {
    Action {
      implicit request =>
        def menuEntries = MenuEntry.dao.findEntries(menu)

        val (viewModel: Option[CMSPageViewModel], versions: List[CMSPageViewModel]) = page match {
          case None =>
            (Some(CMSPageViewModel(System.currentTimeMillis(), "", language, "", connectedUser, "", false, false, menuEntries.length + 1, menu)), List.empty)
          case Some(key) =>
            val versions = CMSPage.dao.findByKeyAndLanguage(key, language)
            if (versions.isEmpty) {
              (None, Seq.empty)
            } else {
              (Some(CMSPageViewModel(versions.head, menu)), versions.map(CMSPageViewModel(_, menu)))
            }
        }

        val menuDefinitions: Seq[java.util.Map[String, String]] = CMSPlugin.getConfiguration.map { config =>
          config.menuDefinitions.map { definition =>
            Map(
              "key" -> definition.key,
              "value" -> definition.title.get(getLang).getOrElse(definition.key)
            ).asJava
          }
        }.getOrElse {
          Seq.empty
        }

        val activeMenuKey = if (viewModel.isDefined) {
          viewModel.get.menu
        } else {
          menu
        }

        if (page.isDefined && versions.isEmpty) {
          NotFound(page.get)
        } else {
          Ok(
            Template(
              'page -> JJson.generate(viewModel),
              'versions -> JJson.generate(Map("versions" -> versions)),
              'languages -> getLanguages,
              'currentLanguage -> language,
              'isNew -> (versions.isEmpty),
              'menuKey -> activeMenuKey,
              'menuDefinitions -> menuDefinitions
            )
          )
        }
    }
  }

  def pageSubmit = CMSAction {
    Action {
      implicit request =>
        CMSPageViewModel.pageForm.bind(request.body.asJson.get).fold(
          formWithErrors => handleValidationError(formWithErrors),
          pageModel => {
            // create / update the entry before we create / update the page since in the implicit conversion above we'll query for that page's position.
            MenuEntry.dao.savePage(pageModel.menu, pageModel.key, pageModel.position, pageModel.title, pageModel.lang, pageModel.published)
            val page: CMSPageViewModel = CMSPageViewModel(CMSPage.dao.create(pageModel.key, pageModel.lang, connectedUser, pageModel.title, pageModel.content, pageModel.published), pageModel.menu)
            CMSPage.dao.removeOldVersions(pageModel.key, pageModel.lang)
            Json(page)
          }
        )
    }
  }

  def pageDelete(key: String, language: String) = CMSAction {
    Action {
      implicit request =>
        CMSPage.dao.delete(key, language)

        // also delete menu entries that refer to that page
        log.info(s"[$connectedUser@${configuration.orgId}] Removed CMS page '$key' in '$language")
        MenuEntry.dao.removePage(key, language)

        Ok
    }
  }

  def pagePreview(key: String, language: String) = CMSAction {
    Action {
      implicit request =>
        CMSPage.dao.find(MongoDBObject("key" -> key, "lang" -> getLang)).$orderby(MongoDBObject("_id" -> -1)).limit(1).toList.headOption match {
          case None => NotFound(key)
          case Some(pagePreview) => Ok(Template('page -> pagePreview))
        }
    }
  }

}

case class CMSPageViewModel(dateCreated: Long,
  key: String, // the key of this page (unique across all version sets of pages)
  lang: String, // 2-letters ISO code of the page language
  title: String, // title of the page in this language
  userName: String, // creator / editor
  content: String, // actual page content (text)
  isSnippet: Boolean = false, // is this a snippet in the welcome page or not
  published: Boolean,
  position: Int,
  menu: String)

object CMSPageViewModel {

  def apply(cmsPage: CMSPage, menu: String)(implicit configuration: OrganizationConfiguration): CMSPageViewModel = {
    // we only allow linking once to a CMSPage so we can be sure that we will only ever find at most one MenuEntry for it
    val (menuEntryPosition, menuKey) = MenuEntry.dao.findOneByTargetPageKey(cmsPage.key).map { e =>
      (e.position, e.menuKey)
    }.getOrElse {
      (MenuEntry.dao.findEntries(menu).length + 1, CMSPlugin.MAIN_MENU)
    }

    CMSPageViewModel(cmsPage._id.getTime, cmsPage.key, cmsPage.lang, cmsPage.title, cmsPage.userName, cmsPage.content, cmsPage.isSnippet, cmsPage.published, menuEntryPosition, menuKey)
  }

  val pageForm = Form(
    mapping(
      "dateCreated" -> of[Long],
      "key" -> text.verifying(pattern("^[-a-z0-9]{3,35}$".r, error = Messages("cms.InvalidKeyValue"))),
      "lang" -> nonEmptyText,
      "title" -> nonEmptyText,
      "userName" -> text,
      "content" -> text,
      "isSnippet" -> boolean,
      "published" -> boolean,
      "position" -> number,
      "menu" -> text
    )(CMSPageViewModel.apply)(CMSPageViewModel.unapply)
  )

}