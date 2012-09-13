package controllers.organization

import play.api.mvc._
import play.api.i18n._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import extensions.Formatters._
import controllers.{BoundController, ViewModel, OrganizationController}
import extensions.{MissingLibs, JJson}
import models._
import cms.{MenuEntry, CMSPage}
import com.mongodb.casbah.Imports._
import core.HubModule
import plugins.CMSPlugin


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object CMS extends BoundController(HubModule) with CMS

trait CMS extends OrganizationController { this: BoundController =>

  val MAIN_MENU = "mainMenu"
  val NO_MENU = "none"

  def CMSAction[A](orgId: String)(action: Action[A]): Action[A] = {
    OrgMemberAction(orgId) {
      Action(action.parser) {
        implicit request => {
          if (organizationServiceLocator.byDomain.isAdmin(orgId, connectedUser) || Group.dao.count(MongoDBObject("users" -> connectedUser, "grantType" -> CMSPlugin.ROLE_CMS_ADMIN.key)) > 0) {
            action(request)
          } else {
            Forbidden(Messages("user.secured.noAccess"))
          }
        }
      }
    }
  }

  implicit def cmsPageToViewModel(p: CMSPage)(implicit configuration: DomainConfiguration) = {
    // for the moment we have one main menu so we can do it like this
    val menuEntryPosition = MenuEntry.dao.findByPageAndMenu(p.orgId, MAIN_MENU, p.key) match {
      case Some(e) => e.position
      case None => MenuEntry.dao.findEntries(p.orgId, MAIN_MENU).length + 1
    }

    val menu = if (MenuEntry.dao.findByPageAndMenu(p.orgId, MAIN_MENU, p.key).isDefined) MAIN_MENU else NO_MENU

    CMSPageViewModel(p._id.getTime, p.key, p.lang, p.title, p.userName, p.content, p.isSnippet, p.published, menuEntryPosition, menu)
  }

  implicit def cmsPageListToViewModelList(l: List[CMSPage])(implicit configuration: DomainConfiguration) = l.map(cmsPageToViewModel(_))


  def list(orgId: String, language: Option[String]) = CMSAction(orgId) {
    Action {
      implicit request =>
        val lang = language.getOrElse(getLang)
        val pages = CMSPage.dao.list(orgId, lang)
        Ok(Template('data -> JJson.generate(Map("pages" -> pages)), 'languages -> getLanguages, 'currentLanguage -> lang))
    }
  }

  def upload(orgId: String) = CMSAction(orgId) {
    Action {
      implicit request =>
        def notSelected(id: ObjectId) = false
        val files = controllers.dos.FileStore.getFilesForItemId(orgId).map(_.asFileUploadResponse(notSelected))
        Ok(Template('uid -> MissingLibs.UUID, 'files -> JJson.generate(files)))
    }
  }

  def uploadSubmit(orgId: String, uid: String) = CMSAction(orgId) {
    Action {
      implicit request =>
        controllers.dos.FileUpload.markFilesAttached(uid, orgId)
        Redirect("/organizations/%s/site/upload".format(orgId))
    }
  }

  def listImages(orgId: String) = CMSAction(orgId) {
    Action {
      implicit request =>
        val images = controllers.dos.FileStore.getFilesForItemId(orgId).filter(_.contentType.contains("image"))

        // tinyMCE stoopidity
        val javascript = "var tinyMCEImageList = new Array(" + images.map(i => """["%s","%s"]""".format(i.name, "/file/image/%s".format(i.id))).mkString(", ") + ");"
        Ok(javascript).as("text/html")
    }
  }

  def page(orgId: String, language: String, page: Option[String]): Action[AnyContent] = CMSAction(orgId) {
    Action {
      implicit request =>
        def menuEntries = MenuEntry.dao.findEntries(orgId, configuration.name, MAIN_MENU)

        val p: (CMSPageViewModel, List[CMSPageViewModel]) = page match {
          case None => (CMSPageViewModel(System.currentTimeMillis(), "", language, "", connectedUser, "", false, false, menuEntries.length + 1, NO_MENU), List.empty)
          case Some(key) =>
            val versions = CMSPage.dao.findByKey(orgId, key)
            if (versions.length == 0) {
              return Action {
                implicit request => NotFound(key)
              }
            }
            (versions.head, versions)
        }

        Ok(Template('page -> JJson.generate(p._1), 'versions -> JJson.generate(Map("versions" -> p._2)), 'languages -> getLanguages, 'currentLanguage -> language, 'isNew -> (p._2.size == 0)))

    }
  }

  def pageSubmit(orgId: String) = CMSAction(orgId) {
    Action {
      implicit request =>
        CMSPageViewModel.pageForm.bind(request.body.asJson.get).fold(
          formWithErrors => handleValidationError(formWithErrors),
          pageModel => {
            // create / update the entry before we create / update the page since in the implicit conversion above we'll query for that page's position.

            if (pageModel.menu == MAIN_MENU) {
              MenuEntry.dao.addPage(orgId, MAIN_MENU, pageModel.key, pageModel.position, pageModel.title, pageModel.lang, pageModel.published)
            } else if (pageModel.menu == NO_MENU) {
              MenuEntry.dao.removePage(orgId, MAIN_MENU, pageModel.key, pageModel.lang)
            }
            val page: CMSPageViewModel = CMSPage.dao.create(orgId, pageModel.key, pageModel.lang, connectedUser, pageModel.title, pageModel.content, pageModel.published)

            Json(page)
          }
        )
    }
  }

  def pageDelete(orgId: String, key: String, language: String) = CMSAction(orgId) {
    Action {
      implicit request =>
        CMSPage.dao.delete(orgId, key, language)

        // also delete menu entries that refer to that page, for now only from the main menu
        MenuEntry.dao.removePage(orgId, MAIN_MENU, key, language)

        Ok
    }
  }

  def pagePreview(orgId: String, langauge: String, key: String) = CMSAction(orgId) {
    Action {
      implicit request =>
        CMSPage.dao.find(MongoDBObject("key" -> key, "lang" -> getLang, "orgId" -> configuration.orgId)).$orderby(MongoDBObject("_id" -> -1)).limit(1).toList.headOption match {
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
                            menu: String,
                            errors: Map[String, String] = Map.empty[String, String]) extends ViewModel

object CMSPageViewModel {

  val pageForm = Form(
    mapping(
      "dateCreated" -> of[Long],
      "key" -> text.verifying(pattern("^[-a-z0-9]{3,35}$".r, error = Messages("org.cms.page.keyInvalid"))),
      "lang" -> nonEmptyText,
      "title" -> nonEmptyText,
      "userName" -> text,
      "content" -> text,
      "isSnippet" -> boolean,
      "published" -> boolean,
      "position" -> number,
      "menu" -> text,
      "errors" -> of[Map[String, String]]
    )(CMSPageViewModel.apply)(CMSPageViewModel.unapply)
  )

}