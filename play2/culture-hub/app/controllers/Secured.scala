package controllers

import play.api.mvc._

/**
 * Secured trait, based on the example in ZenTasks
 */
trait Secured {

  private def username(request: RequestHeader) = request.session.get("userName")

  /**
   * Redirect to login if the user in not authorized.
   */
  private def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Authentication.login)

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
