package controllers

import play.data.validation.{Validation, Required, Email}
import models.User
import play.cache.Cache
import play.libs.Codec
import play.libs.Crypto
import notifiers.Mails
import play.Play

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Registration extends DelvingController {

  import views.Registration._

  def index() = {
    html.index(randomId = Codec.UUID())
  }

  def register() = {
    val code = params.get("code")
    val randomId = params.get("randomID")

    Validation.clear()

    val r: Registration = params.get("registration", classOf[Registration])

    // TODO this should not be done like this, but I haven't figured out yet the way to use the annotation-based way
    Validation.required("registration.firstName", r.firstName)
    Validation.required("registration.lastName", r.lastName)
    Validation.required("registration.email", r.email)
    Validation.email("registration.email", r.email)
    Validation.required("registration.displayName", r.displayName)
    Validation.required("registration.password1", r.password1)
    Validation.required("registration.password2", r.password2)
    if (r.password1 != r.password2) {
      Validation.addError("registration.password1", "Passwords are not the same", r.password1)
      Validation.addError("registration.password2", "Passwords are not the same", r.password2)
    }

    if (Play.id != "test") {
      Validation.equals("code", code, "code", Cache.get(randomId).orNull).message("Invalid code. Please type it again")
    }

    if (User.existsWithEmail(r.email)) Validation.addError("registration.email", "There is already a user with this email address", r.email)
    if (User.existsWithUsername(r.displayName)) Validation.addError("registration.displayName", "There is already a user with this display name", r.displayName)

    Cache.delete(randomId)

    if (Validation.hasErrors) {
      params.flash()
      Validation.keep()
      index()
    } else {
      val activationToken: String = if (Play.id == "test") "testActivationToken" else Codec.UUID()
      val newUser = User(r.firstName, r.lastName, r.email, Crypto.passwordHash(r.password1), r.displayName, false, Some(activationToken), None, false)
      User.insert(newUser)

      try {
        Mails.activation(newUser, activationToken)
        flash += ("registrationSuccess" -> newUser.email)
      } catch {
        case t: Throwable => {
          User.remove(newUser)
          flash += ("registrationError" -> t.getMessage)
        }
      }

      Action(controllers.Application.index)
    }
  }

  def captcha(id: String) = {
    val captcha = play.libs.Images.captcha
    val code = captcha.getText("#E4EAFD")
    Cache.set(id, code, "10mn")
    captcha
  }

  def activate(activationToken: Option[String]) = {
    val success = activationToken.isDefined && User.activateUser(activationToken.get)
    if (success) flash += ("activation" -> "true") else flash += ("activation" -> "false")
    Action(controllers.Application.index)
  }

  def lostPassword() = html.lostPassword()

  def resetPasswordEmail() = {
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
        Validation.addError("email", "No account could be found with this email address", email)
      } else {
        val u = user.get
        if(!u.isActive) {
          Validation.addError("email", "This account is not active yet. Please activate your account with the link sent in the registration e-mail", email)
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

  def resetPassword(resetPasswordToken: Option[String]) = {
    if (resetPasswordToken == None) {
      flash += ("resetPasswordError" -> "Reset token not found")
      Action(controllers.Application.index)
    } else {
      val user = User.canChangePassword(resetPasswordToken.get)
      if(user == None) {
        flash += ("resetPasswordError" -> "Error changing your password. Try resetting it again.")
        Action(controllers.Application.index)
      } else {
        html.resetPassword(resetPasswordToken = resetPasswordToken.get)
      }
    }
  }

  def newPassword() = {
    Validation.clear()
    
    val resetPasswordToken: Option[String] = Option(params.get("resetPasswordToken"))
    val password1: String = params.get("password1")
    val password2: String = params.get("password2")

    if(resetPasswordToken == None) Validation.addError("", "Reset password token not found")

    Validation.required("password1", password1)
    Validation.required("password2", password2)

    if (password1 != password2) {
      Validation.addError("password1", "Passwords are not the same", password1)
      Validation.addError("password2", "Passwords are not the same", password2)
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
                          @Required displayName: String,
                          @Required password1: String,
                          @Required password2: String)

}