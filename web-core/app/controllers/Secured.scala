package controllers

import play.api.mvc._
import Results._
import core.Constants.USERNAME
import play.api.libs.Crypto
import play.api.i18n.Messages

/**
 * Secured trait, based on the example in ZenTasks
 */
trait Secured {

  private def username(request: RequestHeader) = request.session.get(USERNAME)

  private def onUnauthorized(request: RequestHeader): Result = {

    request.cookies.get("rememberme") map {
      remember =>
        val sign = remember.value.substring(0, remember.value.indexOf("-"))
        val username = remember.value.substring(remember.value.indexOf("-") + 1)
        if (Crypto.sign(username) == sign) {
          val authenticateSession = request.session + (USERNAME, username)
          val action = request.session.get("uri") match {
            case Some(uri) => Redirect(uri)
            case None => Redirect("/")
          }
          return action.withSession(authenticateSession)
        }
    }

    val session = request.session - USERNAME + ("uri", if (("GET" == request.method)) request.uri else "/")
    Redirect("/login").withSession(session).flashing(("error" -> Messages("authentication.error")))
  }

  /**
   * Action for authenticated users.
   */
  def IsAuthenticated[A](f: => String => Request[A] => Result) = Security.Authenticated(username, onUnauthorized) {
    user =>
      Action(request => f(user)(request.asInstanceOf[Request[A]]))
  }

  def Authenticated[A](action: Action[A]): Action[A] = {
    Action(action.parser) {
      implicit request =>
        {
          if (username(request).isEmpty) {
            val session = request.session - USERNAME + ("uri", if (("GET" == request.method)) request.uri else "/")
            Redirect("/login").withSession(session)
          } else {
            action(request)
          }
        }
    }
  }
}
