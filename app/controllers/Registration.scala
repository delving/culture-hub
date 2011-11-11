package controllers

import play.data.validation.{Validation, Required, Email}
import play.cache.Cache
import play.libs.Codec
import play.libs.Crypto
import notifiers.Mails
import play.Play
import models.User
import models.Organization
import play.mvc.results.Result
import models.salatContext._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Registration extends DelvingController {

  def index(): Result = {
    Template('randomId -> Codec.UUID())
  }

  def register(): AnyRef = {
    val code = params.get("code")
    val randomId = params.get("randomID")

    Validation.clear()

    val r: Registration = params.get("registration", classOf[Registration])

    // TODO this should not be done like this, but I haven't figured out yet the way to use the annotation-based way
    Validation.required("registration.firstName", r.firstName)
    Validation.required("registration.lastName", r.lastName)
    Validation.required("registration.email", r.email)
    Validation.email("registration.email", r.email)
    Validation.required("registration.userName", r.userName)
    Validation.required("registration.password1", r.password1)
    Validation.required("registration.password2", r.password2)
    if (r.password1 != r.password2) {
      Validation.addError("registration.password2", "registration.passwordsDiffer", r.password2)
    }

    if (Play.id != "test") {
      Validation.equals("code", code, "code", Cache.get(randomId).orNull).message("registration.invalidCode")
    }

    if (User.existsWithEmail(r.email)) Validation.addError("registration.email", "registration.duplicateEmail", r.email)
    if (User.existsWithUsername(r.userName)) Validation.addError("registration.userName", "registration.duplicateDisplayName", r.userName)
    if (Organization.findByOrgId(r.userName) != None) Validation.addError("registration.userName", "registration.duplicateDisplayName", r.userName)

    Cache.delete(randomId)

    if (Validation.hasErrors) {
      params.flash()
      Validation.keep()
      index()
    } else {
      val activationToken: String = if (Play.id == "test") "testActivationToken" else Codec.UUID()
      val newUser = User(userName = r.userName, firstName = r.firstName, lastName = r.lastName, nodes = List(getNode), email = r.email, password = Crypto.passwordHash(r.password1), isActive = false, activationToken = Some(activationToken))
      val inserted = User.insert(newUser)

      inserted match {
        case Some(id) =>
          try {
            Mails.activation(newUser, activationToken)
            flash += ("registrationSuccess" -> newUser.email)
          } catch {
            case t: Throwable => {
              User.remove(newUser.copy(_id = id))
              flash += ("registrationError" -> t.getMessage)
            }
          }
        case None =>
          flash += ("registrationError" -> &("registration.errorCreating"))
      }

      Action(controllers.Application.index)
    }
  }

  def captcha(id: String) = {
    val captcha = play.libs.Images.captcha
    val code = captcha.getText("#000000")
    Cache.set(id, code, "10mn")
    captcha
  }

  def activate(activationToken: Option[String]): AnyRef = {
    val success = activationToken.isDefined && User.activateUser(activationToken.get)
    if (success) flash += ("activation" -> "true") else flash += ("activation" -> "false")
    Action(controllers.Application.index)
  }

  def lostPassword(): Result = Template

  def resetPasswordEmail(): AnyRef = {
    Validation.clear()

    val email = request.params.get("email")
    Validation.required("email", email)
    Validation.email("email", email)

    if(Validation.hasErrors) {
      Validation.keep()
      lostPassword()
    } else {

      // second validation pass
      val user = User.findByEmail(email)
      if(user == None) {
        Validation.addError("email", "registration.accountNotFoundWithEmail", email)
      } else {
        val u = user.get
        if(!u.isActive) {
          Validation.addError("email", "registration.accountNotActive", email)
        }
      }
      if(Validation.hasErrors) {
        Validation.keep()
        lostPassword()
      } else {
        val resetPasswordToken = if (Play.id == "test") "testResetPasswordToken" else Codec.UUID()

        User.preparePasswordReset(user.get, resetPasswordToken)
        Mails.resetPassword(user.get, resetPasswordToken)

        flash += ("resetPasswordEmail" -> "true")
        Action(controllers.Application.index)
      }
    }
  }

  def resetPassword(resetPasswordToken: Option[String]): AnyRef = {
    if (resetPasswordToken == None) {
      flash += ("resetPasswordError" -> &("registration.resetTokenNotFound"))
      Action(controllers.Application.index)
    } else {
      val canChange = User.canChangePassword(resetPasswordToken.get)
      if(!canChange) {
        flash += ("resetPasswordError" -> &("registration.errorPasswordChange"))
        Action(controllers.Application.index)
      } else {
        Template('resetPasswordToken -> resetPasswordToken.get)
      }
    }
  }

  def newPassword(): AnyRef = {
    Validation.clear()
    
    val resetPasswordToken: Option[String] = Option(params.get("resetPasswordToken"))
    val password1: String = params.get("password1")
    val password2: String = params.get("password2")

    if(resetPasswordToken == None) Validation.addError("", "registration.resetTokenNotFound")

    Validation.required("password1", password1)
    Validation.required("password2", password2)

    if (password1 != password2) {
      Validation.addError("password2", "registration.passwordsDiffer", password2)
    }

    if(Validation.hasErrors) {
      Validation.keep()
      resetPassword(resetPasswordToken)
    } else {
      val user: Option[User] = User.findByResetPasswordToken(resetPasswordToken.get)
      // TODO handle the unlikely case in which this guy can't be found anymore
      User.changePassword(resetPasswordToken.get, password1)
      flash += ("resetPasswordSuccess" -> "true")
      Action(controllers.Application.index)
    }

  }

  case class Registration(@Required firstName: String,
                          @Required lastName: String,
                          @Email email: String,
                          @Required userName: String,
                          @Required password1: String,
                          @Required password2: String)

}