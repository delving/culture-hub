package controllers

import models.User
import play.libs.Crypto
import play.data.validation.Validation
import play.Play
import play.mvc.{Util, Before, Controller}
import play.mvc.results.Result

trait Secure {
  self: Controller =>

  @Before(unless = Array("login", "authenticate", "logout"))
  def checkSecurity = {
    session("username") match {
      case Some(username) => {
        val maybeUser: Option[User] = User.find("email = {username}").onParams(username).first()
        maybeUser match {
          case Some(user) => {
            renderArgs += "username" -> user
            Continue
          }
          case None => {
            session.remove("username")
            flash.put("url", if (("GET" == request.method)) request.url else "/")
            Action(Authentication.login)
          }
        }
      }
      case None => {
        flash.put("url", if (("GET" == request.method)) request.url else "/")
        Action(Authentication.login)
      }
    }
  }

  @Util def connectedUser = session.get("username")

}

trait Security {
  def authenticate(username: String, password: String): Boolean
}

object Authentication extends Controller {

  import views.Authentication._

  def login = {
    // TODO this doesn't work because we can't return a Result
    if(session("username").isDefined) {
      Redirect("/")
    }

    val remember = request.cookies.get("rememberme")
    if (remember != null && remember.value.indexOf("-") > 0) {
      var sign: String = remember.value.substring(0, remember.value.indexOf("-"))
      val username: String = remember.value.substring(remember.value.indexOf("-") + 1)
      if (Crypto.sign(username) == sign) {
        session.put("username", username)
        redirectToOriginalURL
      }
    }
    flash.keep("url")
    html.login(title = "Login"))
  }

  def authenticate(): Result = {
    val username: String = params.get("username")
    val password: String = params.get("password")
    val remember: Boolean = params.get("remember").asInstanceOf[Boolean]

    Validation.required("username", username).message("Username is required")
    Validation.required("password", password).message("Password is required")

    if (Validation.hasErrors) {
      flash.keep("url")
      Action(login)
    } else {
      val sec = getSecurity.newInstance.asInstanceOf[ {def authenticate(username: String, password: String): Boolean}]
      val ok = sec.authenticate(username, password)

      if (ok) {
        if (remember) {
          response.setCookie("rememberme", Crypto.sign(username) + "-" + username, "30d")
        }
        session.put("username", username)
        redirectToOriginalURL
      } else {
        flash.keep("url")
        Action(login)
      }
    }
  }

  def logout = {
    session.clear()
    response.removeCookie("rememberme")
    flash.success("secure.logout")
    Action(login)
  }


  private def redirectToOriginalURL = {
    val url = flash.get("url")
    if (url == null) {
      Redirect("/")
    } else {
      Redirect(url)
    }
  }

  private def getSecurity: Class[_] = {
    val classes: java.util.List[Class[_]] = Play.classloader.getAssignableClasses(classOf[Security])
    if (classes.size == 0) {
      classOf[Security]
    }
    else {
      classes.get(0)
    }
  }
}
