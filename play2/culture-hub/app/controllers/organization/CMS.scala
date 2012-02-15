package controllers.organization

import play.api.Play
import play.api.Play.current
import play.api.mvc._
import play.api.i18n._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import extensions.Formatters._
import controllers.{ViewModel, OrganizationController}

//import dos.{StoredFile, FileUploadResponse}

import models._
import com.mongodb.casbah.Imports._

import extensions.JJson
import util.MissingLibs
import controllers.dos.{StoredFile, FileUploadResponse}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object CMS extends OrganizationController {

  val MAIN_MENU = "mainMenu"
  val NO_MENU = "none"

  def CMSAction[A](orgId: String)(action: Action[A]): Action[A] = {
    OrgMemberAction(orgId) {
      Action(action.parser) {
        implicit request => {
          if (Organization.isOwner(orgId, connectedUser) || Group.count(MongoDBObject("users" -> connectedUser, "grantType" -> GrantType.CMS.key)) > 0) {
            action(request)
          } else {
            Forbidden(Messages("user.secured.noAccess"))
          }
        }
      }
    }
  }

  implicit def cmsPageToViewModel(p: CMSPage) = {
    // for the moment we have one main menu so we can do it like this
    val menuEntryPosition = MenuEntry.findByPageAndMenu(p.orgId, p.theme, MAIN_MENU, p.key) match {
      case Some(e) => e.position
      case None => MenuEntry.findEntries(p.orgId, p.theme, MAIN_MENU).length + 1
    }

    val menu = if (MenuEntry.findByPageAndMenu(p.orgId, p.theme, MAIN_MENU, p.key).isDefined) MAIN_MENU else NO_MENU

    CMSPageViewModel(p._id.getTime, p.key, p.theme, p.lang, p.title, p.content, p.isSnippet, menuEntryPosition, menu)
  }

  implicit def cmsPageListToViewModelList(l: List[CMSPage]) = l.map(cmsPageToViewModel(_))


  def list(orgId: String, language: Option[String]) = CMSAction(orgId) {
    Action {
      implicit request =>
        val lang = language.getOrElse(getLang)
        val pages = CMSPage.list(orgId, lang)
        Ok(Template('data -> JJson.generate(Map("pages" -> pages)), 'languages -> getLanguages, 'currentLanguage -> lang))
    }
  }

  def upload(orgId: String) = CMSAction(orgId) {
    Action {
      implicit request =>
        Organization.findByOrgId(orgId).map {
          o =>
            def notSelected(id: ObjectId) = false
            // TODO MIGRATION DOS
            //            val files = controllers.dos.FileStore.getFilesForItemId(o._id).map(_.asFileUploadResponse(notSelected))
            val files = List.empty[FileUploadResponse]
            Ok(Template('uid -> MissingLibs.UUID, 'files -> JJson.generate(files)))
        }.getOrElse(NotFound(orgId))
    }
  }


  def uploadSubmit(orgId: String, uid: String) = CMSAction(orgId) {
    Action {
      implicit request =>
      // use the organization object ID to link the files. we later maybe more lax in the DoS and allow the orgId to be used
        Organization.findByOrgId(orgId).map {
          o =>
          // TODO MIGRATION DOS
          //controllers.dos.FileUpload.markFilesAttached(uid, o._id)
        }
        Redirect("/organizations/%s/site/upload".format(orgId))
    }
  }

  def listImages(orgId: String) = CMSAction(orgId) {
    Action {
      implicit request =>
        Organization.findByOrgId(orgId).map {
          o =>
            val images = List.empty[StoredFile]
            // TODO MIGRATION DOS
            //              val images = controllers.dos.FileStore.getFilesForItemId(o._id).filter(_.contentType.contains("image"))

            // tinyMCE stoopidity
            val javascript = "var tinyMCEImageList = new Array(" + images.map(i => """["%s","%s"]""".format(i.name, "/file/image/%s".format(i.id))).mkString(", ") + ");"
            Ok(javascript).as("text/html")
        }.getOrElse(Ok)
    }
  }

  def page(orgId: String, language: String, page: Option[String]): Action[AnyContent] = CMSAction(orgId) {
    Action {
      implicit request =>
        def menuEntries = MenuEntry.findEntries(orgId, theme.name, MAIN_MENU)

        val p: (CMSPageViewModel, List[CMSPageViewModel]) = page match {
          case None => (CMSPageViewModel(System.currentTimeMillis(), "", theme.name, language, "", "", false, menuEntries.length + 1, NO_MENU), List.empty)
          case Some(key) =>
            val versions = CMSPage.findByKey(orgId, key)
            if (versions.length == 0) {
              return Action {
                implicit request => NotFound(key)
              }
            }
            (versions.head, versions)
        }

        Ok(Template('page -> JJson.generate(p._1), 'versions -> JJson.generate(Map("versions" -> p._2)), 'languages -> getLanguages, 'currentLanguage -> language, 'themes -> getThemes, 'isNew -> (p._2.size == 0)))

    }
  }

  def pageSubmit(orgId: String) = CMSAction(orgId) {
    Action {
      implicit request =>
        CMSPageViewModel.pageForm.bindFromRequest.fold(
          formWithErrors => handleValidationError(formWithErrors),
          pageModel => {
            // create / update the entry before we create / update the page since in the implicit conversion above we'll query for that page's position.

            if (pageModel.menu == MAIN_MENU) {
              MenuEntry.addPage(orgId, theme.name, MAIN_MENU, pageModel.key, pageModel.position, pageModel.title, pageModel.lang)
            } else if (pageModel.menu == NO_MENU) {
              MenuEntry.removePage(orgId, theme.name, MAIN_MENU, pageModel.key, pageModel.lang)
            }
            val page: CMSPageViewModel = CMSPage.create(orgId, theme.name, pageModel.key, pageModel.lang, connectedUser, pageModel.title, pageModel.content)

            Json(page)
          }
        )
    }
  }

  def pageDelete(orgId: String, key: String, language: String) = CMSAction(orgId) {
    Action {
      implicit request =>
        CMSPage.delete(orgId, key, language)

        // also delete menu entries that refer to that page, for now only from the main menu
        MenuEntry.removePage(orgId, theme.name, MAIN_MENU, key, language)

        Ok
    }
  }

  private def getThemes() = if (Play.isDev || PortalTheme.findAll.length == 1) {
    PortalTheme.findAll.map(t => (t.name, t.name))
  } else {
    PortalTheme.findAll.filterNot(_.name == "default").map(t => (t.name, t.name))
  }


}

case class CMSPageViewModel(dateCreated: Long,
                            key: String, // the key of this page (unique across all version sets of pages)
                            theme: String, // the hub theme this page belongs to
                            lang: String, // 2-letters ISO code of the page language
                            title: String, // title of the page in this language
                            content: String, // actual page content (text)
                            isSnippet: Boolean = false, // is this a snippet in the welcome page or not
                            position: Int,
                            menu: String,
                            errors: Map[String, String] = Map.empty[String, String]) extends ViewModel

object CMSPageViewModel {

  val pageForm = Form(
    mapping(
      "dateCreated" -> of[Long],
      "key" -> text.verifying(pattern("^[a-z0-9]{3,15}$".r, error = Messages("org.cms.page.keyInvalid"))),
      "theme" -> nonEmptyText,
      "lang" -> nonEmptyText,
      "title" -> nonEmptyText,
      "content" -> text,
      "isSnippet" -> boolean,
      "position" -> number,
      "menu" -> text,
      "errors" -> of[Map[String, String]]
    )(CMSPageViewModel.apply)(CMSPageViewModel.unapply)
  )

}