package controllers

import core.ThemeAware
import play.api.Play
import play.api.Play.current
import play.templates.groovy.GroovyTemplates
import play.api.mvc._
import extensions.{Extensions, ConfigurationException}
import com.mongodb.casbah.commons.MongoDBObject
import play.api.i18n.Messages
import org.bson.types.ObjectId
import models._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */


trait ApplicationController extends Controller with GroovyTemplates with ThemeAware with Logging with Extensions {

  val LANG = "lang"

  def getLang(implicit request: RequestHeader) = request.session.get(LANG).getOrElse(theme.defaultLanguage)

  def getAuthenticityToken[A](implicit request: Request[A]) = request.session.get(Authentication.AT_KEY).get


  // ~~~ convenience methods - Play's new API around the whole body thing is too fucking verbose

  implicit def withRichBody[A <: AnyContent](body: A) = RichBody(body)

}


case class RichBody[A <: AnyContent](body: A) {

  def getFirstAsString(key: String): Option[String] = body.asFormUrlEncoded match {
    case Some(b) => b.get(key).get.headOption
    case None => None
  }
  
  def getFirstAsObjectId(key: String): Option[ObjectId] = body.asFormUrlEncoded match {
    case Some(b) => b.get(key).getOrElse(return None).headOption.map(id => if(ObjectId.isValid(id)) new ObjectId(id) else null)
    case None => None
  }
}

/**
 * Organization controller making sure you're an owner
 */
trait OrganizationController extends DelvingController with Secured {

  def isOwner: Boolean = renderArgs("isOwner").get.asInstanceOf[Boolean]

  def OrgOwnerAction[A](orgId: String)(action: Action[A]): Action[A] = {
    OrgMemberAction(orgId) {
      Action(action.parser) {
        implicit request => {
          if (isOwner) {
            action(request)
          } else {
            Forbidden(Messages("user.secured.noAccess"))
          }
        }
      }
    }
  }

  def OrgMemberAction[A](orgId: String)(action: Action[A]): Action[A] = {
    Themed {
      OrgBrowsingAction(orgId) {
        Authenticated {
          Action(action.parser) {
            implicit request => {
              if (orgId == null || orgId.isEmpty) {
                Error("How did you even get here?")
              }
              val organizations = request.session.get(AccessControl.ORGANIZATIONS).getOrElse("")
              if (organizations == null || organizations.isEmpty) {
                Forbidden(Messages("user.secured.noAccess"))
              } else if (!organizations.split(",").contains(orgId)) {
                Forbidden(Messages("user.secured.noAccess"))
              }
              renderArgs += ("orgId" -> orgId)
              renderArgs += ("isOwner" -> Organization.isOwner(orgId, userName))
              renderArgs += ("isCMSAdmin" -> (Organization.isOwner(orgId, userName) || (Group.count(MongoDBObject("users" -> userName, "grantType" -> GrantType.CMS.key)) == 0)))
              action(request)
            }
          }
        }
      }
    }
  }
}

trait DelvingController extends ApplicationController with ModelImplicits {

  def getNode = current.configuration.getString("cultureHub.nodeName").getOrElse(throw ConfigurationException("No cultureHub.nodeName provided - this is terribly wrong."))

  def userName(implicit request: RequestHeader) = request.session.get(Authentication.USERNAME).getOrElse(null)

  def Root[A](action: Action[A]): Action[A] = {
    Themed {
      Action(action.parser) {
        implicit request: Request[A] => {

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
          User.findByUsername(userName).map {
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

  def OrgBrowsingAction[A](orgId: String)(action: Action[A]): Action[A] = {
    Root {
      Action(action.parser) {
        implicit request =>
          val orgName = Organization.fetchName(orgId)
          renderArgs +=("browsedOrgName", orgName)
          action(request)
      }
    }
  }

  def connectedUser = renderArgs("userName").map(_.asInstanceOf[String]).getOrElse(null)

  def connectedUserId = renderArgs("userId").map(_.asInstanceOf[ObjectId]).getOrElse(null)

  def browsedUserName: String = renderArgs("browsedUserName").map(_.asInstanceOf[String]).getOrElse(null)

  def browsedUserId: ObjectId = renderArgs("browsedUserId").map(_.asInstanceOf[ObjectId]).getOrElse(null)

  def browsedFullName: String = renderArgs("browsedFullName").map(_.asInstanceOf[String]).getOrElse(null)

  def browsedUserExists: Boolean = renderArgs("browsedUserNotFound") == null

  def browsedIsConnected(implicit request: RequestHeader): Boolean = browsedUserName == request.session.get(Authentication.USERNAME)

  def browsingUser: Boolean = browsedUserName != null

  def isNodeAdmin: Boolean = renderArgs("isNodeAdmin").map(_.asInstanceOf[Boolean]).getOrElse(false)

  // ~~~ convenience methods

  def listPageTitle(itemName: String) = if (browsingUser) Messages("listPageTitle.%s.user".format(itemName), browsedFullName) else Messages("listPageTitle.%s.all".format(itemName))


}


/*
         // Browsed user

*/

/*

if(!browsedUserExists) return NotFound(&("delvingcontroller.userNotFound", renderArgs.get("browsedUserNotFound", classOf[String])))

*/




