package controllers

import play.api.Logger
import play.api.mvc._
import play.api.Play.current
import play.api.data.Form
import play.api.i18n.{Lang, Messages}
import play.libs.Time
import eu.delving.templates.scala.GroovyTemplates
import extensions.{Extensions, ConfigurationException}
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import xml.NodeSeq
import core._
import models.{GrantType, Group, HubUser}
import play.api.data.Forms._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */


trait ApplicationController extends Controller with GroovyTemplates with ThemeAware with Logging with Extensions {

  protected val hubPlugins = current.plugins.filter(_.isInstanceOf[CultureHubPlugin]).map(_.asInstanceOf[CultureHubPlugin])

  private val onApplicationRequestHandlers: Seq[RequestContext => Unit] = hubPlugins.map(_.onApplicationRequest)

  // ~~~ i18n

  private val LANG = "lang"

  private val LANG_COOKIE = "CH_LANG"

  implicit def getLang(implicit request: RequestHeader) = request.cookies.get(LANG_COOKIE).map(_.value).getOrElse(theme.defaultLanguage)

  override implicit def lang(implicit request: RequestHeader): Lang = Lang(getLang)

  def getLanguages = Lang.availables.map(l => (l.language, Messages("locale." + l.language)))

  def ApplicationAction[A](action: Action[A]): Action[A] = {
    Themed {
      Action(action.parser) {
        implicit request: Request[A] => {

          val langParam = request.queryString.get(LANG)

          val requestLanguage = if (langParam.isDefined) {
            Logger("CultureHub").trace("Setting language from parameter to " + langParam.get(0))
            langParam.get(0)
          } else if (request.cookies.get(LANG_COOKIE).isEmpty) {
            // if there is no language for this cookie / user set, set the default one from the PortalTheme
            Logger("CultureHub").trace("Setting language from theme to " + theme.defaultLanguage)
            theme.defaultLanguage
          } else {
            Logger("CultureHub").trace("Setting language from cookie to " + request.cookies.get(LANG_COOKIE).get.value)
            request.cookies.get(LANG_COOKIE).get.value
          }

          val languageChanged = request.cookies.get(LANG_COOKIE).map(_.value) != Some(requestLanguage)

          // just to be clear, this is a feature of the play2 groovy template engine to override the language. due to our
          // action composition being applied after the template has been rendered, we need to pass it in this way
          renderArgs += (__LANG, requestLanguage)

          // apply plugin handlers
          onApplicationRequestHandlers.foreach(handler => handler(RequestContext(request, theme, renderArgs, getLang)))

          // ignore AsyncResults for these things for the moment
          val res = action(request)
          if(res.isInstanceOf[PlainResult]) {
            val r = res.asInstanceOf[PlainResult]
            if (languageChanged) {
              Logger("CultureHub").trace("Composing session after language change")
              r.withCookies(Cookie(name = LANG_COOKIE, value = requestLanguage, maxAge = Time.parseDuration("30d")))
            } else {
              r
            }
          } else {
            res
          }
        }
      }
    }
  }

  //def getAuthenticityToken[A](implicit request: Request[A]) = request.session.get(Authentication.AT_KEY)


  // ~~~ convenience methods - Play's new API around the whole body thing is too fucking verbose

  implicit def withRichBody[A <: AnyContent](body: A) = RichBody(body)

  implicit def withRichQueryString(queryString: Map[String, Seq[String]]) = new {
    def getFirst(key: String): Option[String] = queryString.get(key).getOrElse(return None).headOption
  }

  implicit def withRichSession(session: Session) = new {

    def +(another: Session) = Session(session.data ++ another.data)

  }

  protected def composeSession(actionResult: PlainResult, additionalSession: Session)(implicit request: RequestHeader) = {
    // workaround since withSession calls aren't composable it seems
    val innerSession: Option[Session] = actionResult.header.headers.get(SET_COOKIE).map(cookies => Session.decodeFromCookie(Cookies.decode(cookies).find(_.name == Session.COOKIE_NAME)))
    Logger("CultureHub").trace("Current session: " + session)
    val s = if (innerSession.isDefined) {
      Logger("CultureHub").trace("Composing inner session: " + innerSession.get + " with additional session: " + additionalSession)
      val combined = innerSession.get + additionalSession
      session + combined
    } else {
      session + additionalSession
    }
    Logger("CultureHub").trace("Setting session to: " + s)
    actionResult.withSession(s)
  }

  // ~~~ form handling when using knockout. This returns a map of error messages

  def handleValidationError[T](form: Form[T])(implicit request: RequestHeader) = {
    val fieldErrors = form.errors.filterNot(_.key.isEmpty).map(error => (error.key.replaceAll("\\.", "_"), Messages(error.message, error.args))).toMap
    val globalErrors = form.errors.filter(_.key.isEmpty).map(error => ("global", Messages(error.message, error.args))).toMap

    Json(Map("errors" -> (fieldErrors ++ globalErrors)), BAD_REQUEST)
  }

  // ~~~ API rendering helpers

  def wantsJson(implicit request: RequestHeader) = request.queryString.get("format").isDefined && request.queryString("format").contains("json") ||
    request.queryString.get("format").isEmpty && request.headers.get(ACCEPT).isDefined && request.headers(ACCEPT).contains("application/json")

  def wantsHtml(implicit request: RequestHeader) = request.headers.get(ACCEPT).isDefined && request.headers(ACCEPT).contains("html")

  def wantsXml(implicit request: RequestHeader) = request.queryString.get("format").isDefined && request.queryString("format").contains("xml") ||
    request.queryString.get("format").isEmpty && request.headers.get(ACCEPT).isDefined && request.headers(ACCEPT).contains("application/xml")

  def DOk(xml: NodeSeq, sequences: String*)(implicit request: RequestHeader): Result = {
    if(wantsJson) {
      Ok(util.Json.toJson(xml, false, sequences)).as(JSON)
    } else {
      Ok(xml)
    }
  }


  // ~~~ Form utilities
  import extensions.Formatters._

  val tokenListMapping = list(
    play.api.data.Forms.mapping(
      "id" -> text,
      "name" -> text,
      "tokenType" -> optional(text),
      "data" -> optional(of[Map[String, String]])
      )(Token.apply)(Token.unapply)
    )

}


case class RichBody[A <: AnyContent](body: A) {

  def getFirstAsString(key: String): Option[String] = body.asFormUrlEncoded match {
    case Some(b) => b.get(key).getOrElse(Seq()).headOption
    case None => None
  }

  def getFirstAsObjectId(key: String): Option[ObjectId] = body.asFormUrlEncoded match {
    case Some(b) => b.get(key).getOrElse(return None).headOption.map(id => if (ObjectId.isValid(id)) new ObjectId(id) else null)
    case None => None
  }
}

/**
 * Organization controller making sure you're an owner
 */
trait OrganizationController extends DelvingController with Secured {

  def isOwner(implicit request: RequestHeader): Boolean = renderArgs("isOwner").get.asInstanceOf[Boolean]

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
    OrgBrowsingAction(orgId) {
      Authenticated {
        Action(action.parser) {
          implicit request => {
            if (orgId == null || orgId.isEmpty) {
              Error("How did you even get here?")
            }
            if (!HubUser.findByUsername(connectedUser).map(_.organizations.contains(orgId)).getOrElse(false)) {
              Forbidden(Messages("user.secured.noAccess"))
            } else {
              action(request)
            }
          }
        }
      }
    }
  }
}

trait DelvingController extends ApplicationController with CoreImplicits {

  def getNode = current.configuration.getString("cultureHub.nodeName").getOrElse(throw ConfigurationException("No cultureHub.nodeName provided - this is terribly wrong."))

  def userName(implicit request: RequestHeader) = request.session.get(Constants.USERNAME).getOrElse(null)

  def Root[A](action: Action[A]): Action[A] = {
    ApplicationAction {
      Action(action.parser) {
        implicit request: Request[A] => {

          val additionalSessionParams = new collection.mutable.HashMap[String, String]

          // CSRF check
          // TODO FIXME
//          if (request.method == "POST" && Play.isTest) {
//            val params = request.body match {
//              case body: play.api.mvc.AnyContent if body.asFormUrlEncoded.isDefined => body.asFormUrlEncoded.get
//              case _ => Map.empty[String, Seq[String]] // TODO
//            }
//            val authenticityTokenParam = params.get(key = "authenticityToken")
//            val CSRFHeader = request.headers.get("x-csrf-token")
//            if ((authenticityTokenParam == null && CSRFHeader == null) || (authenticityTokenParam != null && !(Some(authenticityTokenParam) == getAuthenticityToken)) || (CSRFHeader != null && !(CSRFHeader == getAuthenticityToken)))
//            // TODO MIGRATION - PLAY 2 FIXME this does not work!!
//              Forbidden("Bad authenticity token")
//          }

          // Connected user
          HubUser.findByUsername(userName).map {
            u => {
              renderArgs +=("fullName", u.fullname)
              renderArgs +=("userName", u.userName)
              renderArgs +=("userId", u._id)
              //        renderArgs += ("authenticityToken", session.getAuthenticityToken)
              renderArgs +=("organizations", u.organizations)
              renderArgs +=("email", u.email)

              // refresh session parameters
              additionalSessionParams += (Constants.ORGANIZATIONS -> u.organizations.mkString(","))
              additionalSessionParams += (Constants.GROUPS -> u.groups.mkString(","))
            }
          }

          // ignore AsyncResults for these things for the moment
          val res = action(request)
          if(res.isInstanceOf[PlainResult]) {
            val r = res.asInstanceOf[PlainResult]
            Logger("CultureHub").trace("DelvingController composing session with additional parameters " + additionalSessionParams.toMap)
            composeSession(r, Session(additionalSessionParams.toMap))
          } else {
            res
          }
        }
      }
    }
  }

  /**
   * Action in the user space (/bob/object)
   */
  def UserAction[A](user: String)(action: Action[A]): Action[A] = {
    Root {
      Action(action.parser) {
        implicit request =>
          val maybeUser = HubUser.findByUsername(user)
          maybeUser match {
            case Some(u) =>
              renderArgs +=("browsedFullName", u.fullname)
              renderArgs +=("browsedUserId", u._id)
              renderArgs +=("browsedUserName", u.userName)
              action(request)
            case None => NotFound(Messages("delvingcontroller.userNotFound", user))
          }
      }
    }
  }

  def ConnectedUserAction[A](action: Action[A]): Action[A] = {
    Root {
      Authenticated {
        Action(action.parser) {
          implicit request =>
            action(request)
        }
      }
    }
  }

  /**
   * Action secured for the connected user
   */
  def SecuredUserAction[A](user: String)(action: Action[A]): Action[A] = {
    UserAction(user) {
      Authenticated {
        Action(action.parser) {
          implicit request => {
            if (connectedUser != user) {
              Forbidden(Messages("user.secured.noAccess"))
            } else {
              action(request)
            }
          }
        }
      }
    }
  }

  def OrgBrowsingAction[A](orgId: String)(action: Action[A]): Action[A] = {
    Root {
      Action(action.parser) {
        implicit request =>
          val orgName = HubServices.organizationService.getName(orgId, "en")
          val isAdmin = HubServices.organizationService.isAdmin(orgId, userName)
          renderArgs += ("orgId" -> orgId)
          renderArgs += ("browsedOrgName", orgName)
          renderArgs += ("currentLanguage", getLang)
          renderArgs += ("isAdmin" -> isAdmin)

          val roles: Seq[String] = (session.get("userName").map {
            u => Group.findDirectMemberships(userName, orgId).map(g => g.grantType).toSeq
          }.getOrElse {
            List.empty
          }) ++ (if(isAdmin) Seq(GrantType.OWN.key) else Seq.empty)


          import collection.JavaConverters._

          renderArgs += ("roles" -> roles.asJava)

          val navigation = hubPlugins.map(
            plugin => plugin.getNavigation(Map("orgId" -> orgId, "currentLanguage" -> getLang), roles, HubUser.findByUsername(connectedUser).map(u => u.organizations.contains(orgId)).getOrElse(false)).map(_.asJavaMap)
          ).flatten.asJava

          renderArgs += ("navigation" -> navigation)


          action(request)
      }
    }
  }

  def isConnected(implicit request: RequestHeader) = request.session.get(Constants.USERNAME).isDefined

  def connectedUser(implicit request: RequestHeader) = renderArgs("userName").map(_.asInstanceOf[String]).getOrElse(null)

  def browsedUserName(implicit request: RequestHeader): String = renderArgs("browsedUserName").map(_.asInstanceOf[String]).getOrElse(null)

  def browsedFullName(implicit request: RequestHeader): String = renderArgs("browsedFullName").map(_.asInstanceOf[String]).getOrElse(null)

  def browsingUser(implicit request: RequestHeader): Boolean = browsedUserName != null

  // ~~~ convenience methods

  def listPageTitle(itemName: String)(implicit request: RequestHeader) = if (browsingUser) Messages("listPageTitle.%s.user".format(itemName), browsedFullName) else Messages("listPageTitle.%s.all".format(itemName))


}