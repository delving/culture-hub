package controllers

import play.data.validation.{Valid, Validation, Required, Email}
import models.User

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Registration extends DelvingController {

  import views.Registration._

  def index() = html.index()

  def register() = {
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
    if(r.password1 != r.password2) {
      Validation.addError("registration.password1", "Passwords are not the same", r.password1)
      Validation.addError("registration.password2", "Passwords are not the same", r.password2)
    }

    // TODO check email uniqueness
    // TODO check displayName uniqueness

    if (Validation.hasErrors) {
      params.flash()
      Validation.keep()
      index()
    } else {
      val newUser = User(r.firstName, r.lastName, r.email, r.password1, r.displayName, false)
      User.insert(newUser)

      // TODO user feedback (flash scope message)
      // TODO send email to the user
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