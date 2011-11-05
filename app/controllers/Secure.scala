package controllers


import models.User
import play.libs.Crypto
import play.data.validation.Validation
import play.Play
import play.mvc.{Util, Before, Controller}
import play.mvc.results.Result
import play.i18n.Messages
import com.mongodb.casbah.commons.MongoDBObject
import play.mvc.Scope.Session

trait Secure {
  self: DelvingController =>

  import Authentication.USERNAME

  @Before(unless = Array("login", "authenticate", "logout"))
  def checkSecurity = {
    session(USERNAME) match {
      case Some(userName) => {
        val maybeUser: Option[User] = User.findOne(MongoDBObject(USERNAME -> userName))
        maybeUser match {
          case Some(user) => {
            renderArgs += USERNAME -> user.userName
            Continue
          }
          case None => {
            session.remove(USERNAME)
            session.put("url", if (("GET" == request.method)) request.url else "/")
            Action(Authentication.login())
          }
        }
      }
      case None => {
        session.put("url", if (("GET" == request.method)) request.url else "/")
        Action(Authentication.login())
      }
    }
  }
}

trait UserAuthentication {
  self: Controller =>

  import Authentication.USERNAME
  @Util def connectedUser = session.get(USERNAME)
}

/**
 * Exception thrown when a user is not yet active
 */
class InactiveUserException extends Exception

trait Security {
  def authenticate(username: String, password: String): Boolean
  def onAuthenticated(username: String, session: Session)
}

object Authentication extends Controller with ThemeAware {

  val USERNAME = "userName"

  private val authSec = getSecurity.newInstance.asInstanceOf[ { def authenticate(username: String, password: String): Boolean }]

  private val onAuthSec = getSecurity.newInstance.asInstanceOf[ { def onAuthenticated(username: String, session: Session) }]

  def login(): AnyRef = {
    if(session(USERNAME).isDefined) Redirect("/")

    val remember = request.cookies.get("rememberme")
    if (remember != null && remember.value.indexOf("-") > 0) {
      val sign: String = remember.value.substring(0, remember.value.indexOf("-"))
      val username: String = remember.value.substring(remember.value.indexOf("-") + 1)
      if (Crypto.sign(username) == sign) {
        session.put(USERNAME, username)
        redirectToOriginalURL
      }
    }
    Template
  }

  def authenticate(): AnyRef = {
    val username: String = params.get("username")
    val password: String = params.get("password")
    val remember: Boolean = params.get("remember") == "true"

    Validation.required("username", username).message("authentication.usernameRequired")
    Validation.required("password", password).message("authentication.passwordRequired")

    if (Validation.hasErrors) {
      loginError()
    } else {
      try {
        if (authSec.authenticate(username, password)) {
          if (remember) {
            response.setCookie("rememberme", Crypto.sign(username) + "-" + username, "30d")
          }
          session.put(USERNAME, username)
          onAuthSec.onAuthenticated(username, session)
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

  def loginError(): Result = {
    flash.error(Messages.get("authentication.error"))
    params.flash()
    Template("/Authentication/login.html")
  }

  def userNotActiveError(): Result = {
    flash.error(Messages.get("authentication.notactive.error"))
    params.flash()
    Template("/Authentication/login.html")
  }

  def logout = {
    session.clear()
    response.removeCookie("rememberme")
    flash.success(Messages.get("authentication.logout"))
    Action(login)
  }


  private def redirectToOriginalURL: Result = {
    val url = session.get("url")
    if (url == null) {
      Redirect("/")
    } else {
      session.remove("url")
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
