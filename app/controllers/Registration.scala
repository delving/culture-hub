package controllers

import play.data.validation.{Valid, Validation, Required, Email}
import models.User
import play.cache.Cache
import play.libs.Codec
import notifiers.Mails

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
    Validation.equals("code", code, "code", Cache.get(randomId).orNull).message("Invalid code. Please type it again")

    if (User.existsWithEmail(r.email)) Validation.addError("registration.email", "There is already a user with this email address", r.email)
    if (User.existsWithDisplayName(r.displayName)) Validation.addError("registration.displayName", "There is already a user with this display name", r.displayName)

    Cache.delete(randomId)

    if (Validation.hasErrors) {
      params.flash()
      Validation.keep()
      index()
    } else {
      val newUser = User(r.firstName, r.lastName, r.email, r.password1, r.displayName, false)
      User.insert(newUser)

      try {
        Mails.activation(newUser)
        flash += ("success" -> newUser.email)
      } catch {
        case t:Throwable => {
          User.remove(newUser)
          flash += ("error" -> t.getMessage)
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

  case class Registration(@Required firstName: String,
                          @Required lastName: String,
                          @Email email: String,
                          @Required displayName: String,
                          @Required password1: String,
                          @Required password2: String)

}