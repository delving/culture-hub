package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.libs.Crypto
import play.libs.Time
import play.api.i18n.Messages
import extensions.MissingLibs
import models.{OrganizationConfiguration, HubUser}
import core._
import play.api.mvc.Cookie

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Authentication extends BoundController(HubModule) with Authentication

trait Authentication extends ApplicationController { this: BoundController =>

  val authenticationServiceLocator = inject [ DomainServiceLocator[AuthenticationService] ]
  val userProfileServiceLocator = inject [ DomainServiceLocator[UserProfileService] ]

  val REMEMBER_COOKIE = "rememberme"
  val AT_KEY = "___AT" // authenticity token

  case class Auth(userName: String, password: String)

  def loginForm(implicit configuration: OrganizationConfiguration) = Form(
    tuple(
      "userName" -> nonEmptyText,
      "password" -> nonEmptyText,
      "remember" -> boolean
    ) verifying(Messages("authentication.error"), result => result match {
      case (u, p, r) =>
        authenticationServiceLocator.byDomain.connect(resolveEmail(u), p)
    }))

  private def resolveEmail(userName: String)(implicit configuration: OrganizationConfiguration) = if (userName.contains("@")) {
      HubUser.dao.findOneByEmail(userName).map(_.userName).getOrElse(userName)
    } else {
      userName
    }

  def login = ApplicationAction {
    Action {
      implicit request =>
        if(session.get("userName").isDefined) {
          Redirect(controllers.routes.Application.index)
        } else {
          Ok(Template('loginForm -> loginForm))
        }
    }
  }

  def logout = Action {
    Redirect(routes.Authentication.login).withNewSession.discardingCookies(REMEMBER_COOKIE).flashing(
      "success" -> Messages("authentication.logout")
    )
  }

  /**
   * Handle login form submission.
   */
  def authenticate: Action[AnyContent] = ApplicationAction {
    Action {
      implicit request =>
        loginForm.bindFromRequest.fold(
          formWithErrors => BadRequest(Template("/Authentication/login.html", 'loginForm -> formWithErrors)),
          user => {
            val userName = resolveEmail(user._1)
            // first check if the user exists in this hub
            HubUser.dao.findByUsername(userName).orElse {
              // create a local user
              userProfileServiceLocator.byDomain.getUserProfile(userName).flatMap { p =>
                val newHubUser = HubUser(userName = userName,
                  firstName = p.firstName,
                  lastName = p.lastName,
                  email = p.email,
                  userProfile = models.UserProfile(
                    isPublic = p.isPublic,
                    fixedPhone = p.fixedPhone,
                    description = p.description,
                    funFact = p.funFact,
                    websites = p.websites,
                    twitter = p.twitter,
                    linkedIn = p.linkedIn
                  )
                )
                HubUser.dao.insert(newHubUser)
                HubUser.dao.findByUsername(userName)
              }
            }.map { u =>
              val action = (request.session.get("uri") match {
                case Some(uri) => Redirect(uri)
                case None => Redirect(controllers.routes.Application.index)
              }).withSession(
                Constants.USERNAME -> userName,
                AT_KEY -> authenticityToken)

              if (user._3) {
                action.withCookies(Cookie(
                  name = REMEMBER_COOKIE,
                  value = Crypto.sign(user._1) + "-" + user._1,
                  maxAge = Time.parseDuration("30d")
                ))
              } else {
                action
              }
            }.getOrElse {
              ErrorReporter.reportError(request, "Could not create local HubUser for user %s".format(userName))
              Redirect(controllers.routes.Authentication.login).flashing(("error", "Sorry, something went wrong while logging in, please try again"))
            }
          }
        )
    }
  }

  private def authenticityToken = Crypto.sign(MissingLibs.UUID)

}