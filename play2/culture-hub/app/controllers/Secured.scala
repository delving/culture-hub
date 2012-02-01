package controllers

import play.api.mvc._
import Results._
import play.api.libs.Crypto

/**
 * Secured trait, based on the example in ZenTasks
 */
trait Secured {

  val USERNAME = "userName"

  private def username(request: RequestHeader) = request.session.get(USERNAME)

  private def onUnauthorized(request: RequestHeader): Result = {
    request.session - USERNAME
    request.session +("uri", if (("GET" == request.method)) request.uri else "/")

    request.cookies.get("rememberme") map {
      remember =>
        val sign = remember.value.substring(0, remember.value.indexOf("-"))
        val username = remember.value.substring(remember.value.indexOf("-") + 1)
        if (Crypto.sign(username) == sign) {
          request.session +(USERNAME, username)
          return request.session.get("uri") match {
            case Some(uri) => Redirect(uri)
            case None => Redirect(controllers.routes.Application.index)
          }
        }
    }
    Redirect(routes.Authentication.login)
  }

  /**
   * Action for authenticated users.
   */
  def IsAuthenticated(f: => String => Request[AnyContent] => Result) = Security.Authenticated(username, onUnauthorized) {
    user =>
      Action(request => f(user)(request))
  }

  /**
   * Check if the connected user is a member of this project.
   */
  def IsMemberOf(project: Long)(f: => String => Request[AnyContent] => Result) = IsAuthenticated {
    user => request =>
      Results.Forbidden
  }

}
