package controllers

import play.api.Logger
import play.api.mvc._
import play.api.Play.current
import play.api.i18n.{ Lang, Messages }
import play.libs.Time
import eu.delving.templates.scala.GroovyTemplates
import collection.JavaConverters._
import org.bson.types.ObjectId
import core._
import models.{ OrganizationConfiguration, Role, Group, HubUser }

/**
 * TODO document the default renderArgs attributes available to templates
 *
 * orgId
 * isAdmin
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait ApplicationController extends Controller with GroovyTemplates with ControllerBase {

  // ~~~ i18n

  private val LANG = "lang"

  private val LANG_COOKIE = "CH_LANG"

  implicit def getLang(implicit request: RequestHeader) = request.cookies.get(LANG_COOKIE).map(_.value).getOrElse(configuration.ui.defaultLanguage)

  override implicit def lang(implicit request: RequestHeader): Lang = Lang(getLang)

  def getLanguages = Lang.availables.map(l => (l.language, Messages("locale." + l.language)))

  def ApplicationAction[A](action: Action[A]): Action[A] = {
    OrganizationConfigured {
      Action(action.parser) {
        implicit request: Request[A] =>
          {

            renderArgs += ("themeInfo" -> new ThemeInfo(configuration))

            val langParam = request.queryString.get(LANG)

            val requestLanguage = if (langParam.isDefined) {
              Logger("CultureHub").trace("Setting language from parameter to " + langParam.get(0))
              langParam.get(0)
            } else if (request.cookies.get(LANG_COOKIE).isEmpty) {
              // if there is no language for this cookie / user set, set the default one from the configuration
              Logger("CultureHub").trace("Setting language from domain configuration to " + configuration.ui.defaultLanguage)
              configuration.ui.defaultLanguage
            } else {
              Logger("CultureHub").trace("Setting language from cookie to " + request.cookies.get(LANG_COOKIE).get.value)
              request.cookies.get(LANG_COOKIE).get.value
            }

            val languageChanged = request.cookies.get(LANG_COOKIE).map(_.value) != Some(requestLanguage)

            // just to be clear, this is a feature of the play2 groovy template engine to override the language. due to our
            // action composition being applied after the template has been rendered, we need to pass it in this way
            renderArgs += (__LANG -> requestLanguage)

            // main navigation
            val menu = CultureHubPlugin.getEnabledPlugins.map(
              plugin => plugin.mainMenuEntries(configuration, getLang).map(_.asJavaMap)
            ).flatten.asJava

            renderArgs += ("menu" -> menu)

            // ignore AsyncResults for these things for the moment
            val res = action(request)
            if (res.isInstanceOf[PlainResult]) {
              val r = res.asInstanceOf[PlainResult]
              if (languageChanged) {
                Logger("CultureHub").trace("Composing session after language change")
                r.withCookies(Cookie(name = LANG_COOKIE, value = requestLanguage, maxAge = Some(Time.parseDuration("30d"))))
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

  def getFirstAsBoolean(key: String): Option[Boolean] = body.asFormUrlEncoded match {
    case Some(b) => b.get(key).getOrElse(return None).headOption.map(b => b.toBoolean)
    case None => None
  }
}

/**
 * Organization controller making sure you're an owner
 */
trait OrganizationController extends DelvingController with Secured {

  def isAdmin(implicit request: RequestHeader, configuration: OrganizationConfiguration): Boolean = organizationServiceLocator.byDomain.isAdmin(configuration.orgId, connectedUser)

  def isAdmin(orgId: String)(implicit request: RequestHeader): Boolean = organizationServiceLocator.byDomain.isAdmin(orgId, connectedUser)

  def isMember(implicit request: RequestHeader, configuration: OrganizationConfiguration) = {
    !HubUser.dao.findByUsername(connectedUser).map(_.organizations.contains(configuration.orgId)).getOrElse(false)
  }

  def OrganizationAdmin[A](action: Action[A]): Action[A] = {
    OrganizationMember {
      Action(action.parser) {
        implicit request =>
          {
            if (isAdmin) {
              action(request)
            } else {
              Forbidden(Messages("user.secured.noAccess"))
            }
          }
      }
    }
  }

  def OrganizationMember[A](action: Action[A]): Action[A] = {
    OrganizationBrowsing {
      Authenticated {
        Action(action.parser) {
          implicit request =>
            {
              if (isMember) {
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

trait DelvingController extends ApplicationController {

  val organizationServiceLocator = HubModule.inject[DomainServiceLocator[OrganizationService]](name = None)

  def userName(implicit request: RequestHeader) = request.session.get(Constants.USERNAME).getOrElse(null)

  def Root[A](action: Action[A]): Action[A] = {
    ApplicationAction {
      Action(action.parser) {
        implicit request: Request[A] =>
          {

            val additionalSessionParams = new scala.collection.mutable.HashMap[String, String]

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

            // orgId
            renderArgs += ("orgId" -> configuration.orgId)

            // admin
            val isAdmin = organizationServiceLocator.byDomain.isAdmin(configuration.orgId, userName)
            renderArgs += ("isAdmin" -> isAdmin.asInstanceOf[AnyRef])

            // Connected user
            HubUser.dao.findByUsername(userName).foreach { u =>
              renderArgs += ("fullName" -> u.fullname)
              renderArgs += ("userName" -> u.userName)
              renderArgs += ("userId" -> u._id)
              //        renderArgs += ("authenticityToken", session.getAuthenticityToken)
              renderArgs += ("organization" -> u.organizations.headOption.getOrElse(""))
              renderArgs += ("email" -> u.email)
            }

            // SearchIn

            val searchIn: Map[String, String] = CultureHubPlugin.getEnabledPlugins.flatMap { p =>
              p.getServices(classOf[SearchInService]).map { service =>
                service.getSearchInTargets(Option(connectedUser))
              }
            }.foldLeft(Map.empty[String, String]) { _ ++ _ }

            renderArgs += ("searchIn" -> searchIn.asJava)

            // breadcrumbs
            renderArgs += ("breadcrumbs" -> Breadcrumbs.crumble())

            // ignore AsyncResults for these things for the moment
            val res = action(request)
            if (res.isInstanceOf[PlainResult]) {
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
          val maybeUser = HubUser.dao.findByUsername(user)
          maybeUser match {
            case Some(u) =>
              renderArgs += ("browsedFullName" -> u.fullname)
              renderArgs += ("browsedUserId" -> u._id)
              renderArgs += ("browsedUserName" -> u.userName)
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
          implicit request =>
            {
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

  def OrganizationBrowsing[A](action: Action[A]): Action[A] = {
    Root {
      Action(action.parser) {
        implicit request =>
          renderArgs += ("currentLanguage" -> getLang)

          val roles: Seq[String] = (session.get("userName").map {
            u => Group.dao.findDirectMemberships(userName).map(g => g.roleKey).toSeq
          }.getOrElse {
            List.empty
          }) ++ (if (renderArgs("isAdmin").map(_.asInstanceOf[Boolean]).getOrElse(false)) Seq(Role.OWN.key) else Seq.empty)

          renderArgs += ("roles" -> roles.asJava)

          val navigation = CultureHubPlugin.getEnabledPlugins.map {
            plugin =>
              plugin.
                getOrganizationNavigation(
                  orgId = configuration.orgId,
                  lang = getLang,
                  roles = roles,
                  isMember = HubUser.dao.findByUsername(connectedUser).map(u => u.organizations.contains(configuration.orgId)).getOrElse(false)
                ).map(_.asJavaMap)
          }.flatten.asJava

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

  // ~~~ Access control

  def getUserGrantTypes(orgId: String)(implicit request: RequestHeader, configuration: OrganizationConfiguration): Seq[Role] = request.session.get(Constants.USERNAME).map {
    userName =>
      val isAdmin = organizationServiceLocator.byDomain.isAdmin(orgId, userName)
      val groups: Seq[Role] = Group.dao.findDirectMemberships(userName).map(_.roleKey).toSeq.distinct.map(Role.get(_))
      // TODO make this cleaner
      if (isAdmin) {
        groups ++ Seq(Role.get("own"))
      } else {
        groups
      }
  }.getOrElse(Seq.empty)

}