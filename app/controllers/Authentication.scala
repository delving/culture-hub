package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.libs.Crypto
import play.libs.Time
import play.api.i18n.Messages
import extensions.MissingLibs
import models.{DomainConfiguration, HubUser}
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

  def loginForm(implicit configuration: DomainConfiguration) = Form(
    tuple(
      "userName" -> nonEmptyText,
      "password" -> nonEmptyText,
      "remember" -> boolean
    ) verifying(Messages("authentication.error"), result => result match {
      case (u, p, r) => authenticationServiceLocator.byDomain.connect(u, p)
    }))

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
    Action { implicit request =>
      loginForm.bindFromRequest.fold(
        formWithErrors => BadRequest(Template("/Authentication/login.html", 'loginForm -> formWithErrors)),
        user => {

          HubUser.dao.findByUsername(user._1).map { u =>

            val action = (request.session.get("uri") match {
              case Some(uri) => Redirect(uri)
              case None => Redirect(controllers.routes.Application.index)
            }).withSession(
              Constants.USERNAME -> user._1,
              "connectedUserId" -> u._id.toString,
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
            Redirect(controllers.routes.Authentication.login).flashing(("error", "Sorry, something went wrong while logging in, please try again"))
          }


        }
      )
  }
  }

  private def authenticityToken = Crypto.sign(MissingLibs.UUID)

}