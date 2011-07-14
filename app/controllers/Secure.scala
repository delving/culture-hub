package controllers


import models.User
import play.libs.Crypto
import play.data.validation.Validation
import play.Play
import play.mvc.{Util, Before, Controller}
import play.mvc.results.Result
import play.templates.Html
import play.i18n.Messages
import com.mongodb.casbah.commons.MongoDBObject

trait Secure {
  self: Controller =>

  @Before(unless = Array("login", "authenticate", "logout"))
  def checkSecurity = {
    session("username") match {
      case Some(username) => {
        val maybeUser: Option[User] = User.findOne(MongoDBObject("email" -> username))
        maybeUser match {
          case Some(user) => {
            renderArgs += "username" -> user
            Continue
          }
          case None => {
            session.remove("username")
            flash.put("url", if (("GET" == request.method)) request.url else "/")
            Action(Authentication.login())
          }
        }
      }
      case None => {
        flash.put("url", if (("GET" == request.method)) request.url else "/")
        Action(Authentication.login())
      }
    }
  }
}

trait UserAuthentication {
  self: Controller =>

  @Util def connectedUser = session.get("username")
}

/**
 * Exception thrown when a user is not yet active
 */
class InactiveUserException extends Exception

trait Security {
  def authenticate(username: String, password: String): Boolean
}

object Authentication extends Controller {

  import views.Authentication._

  def login():AnyRef = {
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
      html.login(title = "Login")
  }

  def authenticate(): AnyRef = {
    val username: String = params.get("username")
    val password: String = params.get("password")
    val remember: Boolean = params.get("remember") == "true"

    Validation.required("username", username).message("Username is required")
    Validation.required("password", password).message("Password is required")

    if (Validation.hasErrors) {
      loginError()
    } else {
      val sec = getSecurity.newInstance.asInstanceOf[ {def authenticate(username: String, password: String): Boolean}]
      try {
        if (sec.authenticate(username, password)) {
          if (remember) {
            response.setCookie("rememberme", Crypto.sign(username) + "-" + username, "30d")
          }
          session.put("username", username)
          redirectToOriginalURL
        } else {
          loginError()
        }
      } catch {
        case iue: InactiveUserException => userNotActiveError()
        case _ => loginError()
      }

    }
  }

  def loginError(): Html = {
    flash.keep("url")
    flash.error(Messages.get("secure.error"))
    params.flash()
    html.login(title = "Login")
  }

  def userNotActiveError(): Html = {
    flash.keep("url")
    flash.error(Messages.get("secure.notactive.error"))
    params.flash()
    html.login(title = "Login")
  }

  def logout = {
    session.clear()
    response.removeCookie("rememberme")
    flash.success(Messages.get("secure.logout"))
    Action(login)
  }


  private def redirectToOriginalURL:Result = {
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
