package controllers

import core.ThemeAware
import play.api.Play
import play.api.Play.current
import play.templates.groovy.GroovyTemplates
import play.api.mvc._
import models.{Organization, MenuEntry, User}
import extensions.{Extensions, ConfigurationException}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */


trait ApplicationController extends Controller with GroovyTemplates with ThemeAware with Logging with Extensions {

  val LANG = "lang"

  def getLang(implicit request: RequestHeader) = request.session.get(LANG).getOrElse(theme.defaultLanguage)

  def getAuthenticityToken[A](implicit request: Request[A]) = request.session.get(Authentication.AT_KEY).get

}

trait DelvingController extends ApplicationController with ModelImplicits {

  def getNode = current.configuration.getString("cultureHub.nodeName").getOrElse(throw ConfigurationException("No cultureHub.nodeName provided - this is terribly wrong."))

  def Root[A](action: Action[A]): Action[A] = {
    Themed {
      Action(action.parser) {
        implicit request: Request[A] => {

          val connectedUser = request.session.get(Authentication.USERNAME).getOrElse("")
          val additionalSessionParams = new collection.mutable.HashMap[String, String]

          // CSRF check
          if (request.method == "POST" && Play.isTest) {
            val params = request.body match {
              case body: play.api.mvc.AnyContent if body.asFormUrlEncoded.isDefined => body.asFormUrlEncoded.get
              case _ => Map.empty[String, Seq[String]] // TODO
            }
            val authenticityTokenParam = params.get(key = "authenticityToken")
            val CSRFHeader = request.headers.get("x-csrf-token")
            if ((authenticityTokenParam == null && CSRFHeader == null) || (authenticityTokenParam != null && !(authenticityTokenParam == getAuthenticityToken)) || (CSRFHeader != null && !(CSRFHeader.get == getAuthenticityToken)))
              Forbidden("Bad authenticity token")
          }


          // Connected user
          User.findByUsername(connectedUser).map {
            u => {
              renderArgs +=("fullName", u.fullname)
              renderArgs +=("userName", u.userName)
              renderArgs +=("userId", u._id)
              //        renderArgs += ("authenticityToken", session.getAuthenticityToken)
              renderArgs +=("organizations", u.organizations)
              renderArgs +=("email", u.email)
              renderArgs +=("isNodeAdmin", u.nodesAdmin.contains(getNode))

              // refresh session parameters
              additionalSessionParams += (AccessControl.ORGANIZATIONS -> u.organizations.mkString(","))
              additionalSessionParams += (AccessControl.GROUPS -> u.groups.mkString(","))
            }
          }

          // Language

          // if a lang param is passed, this is a request to explicitly change the language
          // and will change it in the user's cookie
          val lang = request.queryString.get("lang")
          if (lang.isDefined) {
            additionalSessionParams += (LANG -> lang.get(0))
          }

          // if there is no language for this cookie / user set, set the default one from the PortalTheme
          if (request.session.get(LANG).isEmpty) {
            additionalSessionParams += (LANG -> theme.defaultLanguage)
          }

          // Menu entries
          val mainMenuEntries = MenuEntry.findEntries(theme.name, CMS.MAIN_MENU).filterNot(!_.title.contains(getLang)).map(e => (Map(
            "title" -> e.title(getLang),
            "page" -> e.targetPageKey.getOrElse("")))
          ).toList
          renderArgs +=("menu", mainMenuEntries)

          // TODO
          //        Option(params.get("orgId")).map {
          //              orgId =>
          //                val orgName = Organization.fetchName(orgId)
          //                renderArgs += ("browsedOrgName", orgName)
          //            }


          val newSession = additionalSessionParams.foldLeft[Session](request.session) {
            _ + _
          }
          val r: PlainResult = action(request).asInstanceOf[PlainResult]
          r.withSession(newSession)
        }
      }
    }
  }

  /**
   * Action in the user space (/bob/object)
   */
  def UserAction[A](action: Action[A])(implicit user: String): Action[A] = {
    Root {
      Action(action.parser) {
        implicit request =>
          val maybeUser = User.findByUsername(user)
          maybeUser match {
            case Some(u) =>
              renderArgs +=("browsedFullName", u.fullname)
              renderArgs +=("browsedUserId", u._id)
              renderArgs +=("browsedUserName", u.userName)
            case None =>
              renderArgs +=("browsedUserNotFound", user)
          }
          action(request)
      }
    }
  }

  def OrgAction[A](action: Action[A])(implicit orgId: String): Action[A] = {
    Root {
      Action(action.parser) {
        implicit request =>
          val orgName = Organization.fetchName(orgId)
          renderArgs +=("browsedOrgName", orgName)
          action(request)
      }
    }
  }


}


/*
         // Browsed user

*/

/*

if(!browsedUserExists) return NotFound(&("delvingcontroller.userNotFound", renderArgs.get("browsedUserNotFound", classOf[String])))

*/




