package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.templates.groovy.GroovyTemplates
import core.{ThemeInfo, ThemeAware}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Authentication extends Controller with GroovyTemplates with ThemeAware {

  case class Auth(userName: String, password: String)

  val loginForm = Form(
    tuple(
      "userName" -> text,
      "password" -> text
    ) verifying("Invalid username or password", result => result match {
      case (u, p) => true
    }))

  def login = Themed {
    Action {
      implicit request =>
        renderArgs += ("themeInfo" -> new ThemeInfo(theme))
        Ok(Template)
    }
  }

  /**
   * Handle login form submission.
   */
  def authenticate = Action {
    implicit request =>
      loginForm.bindFromRequest.fold(
        errors => Results.Redirect(routes.Authentication.login), // TODO re-redirect to login
        user => {
          Ok
          //          Redirect(routes.Projects.index).withSession("email" -> user._1)
        }
      )
  }

}