package controllers.user

import controllers._
import models._

import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import extensions.Formatters._
import play.api.i18n._
import extensions.JJson
import core.HubServices

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends DelvingController {

  def profile(user: String) = SecuredUserAction(user) {
    Action {
      implicit request =>
        val u = User.findByUsername(connectedUser).get
        val p = u.userProfile
        val profile = ProfileViewModel(p.isPublic, u.firstName, u.lastName, u.email, p.description.getOrElse(""), p.funFact.getOrElse(""), p.websites, p.twitter.getOrElse(""), p.linkedIn.getOrElse(""))
        Ok(Template('data -> JJson.generate(profile), 'profileForm -> ProfileViewModel.profileForm))
    }
  }

  def profileSubmit(user: String) = SecuredUserAction(user) {
    Action {
      implicit request =>
        ProfileViewModel.profileForm.bindFromRequest.fold(
          formWithErrors => handleValidationError(formWithErrors),
          profileModel => {
            def StrictOption(s: String) = Option(s).filter(_.trim.nonEmpty)

            val updated = HubServices.userProfileService.updateUserProfile(connectedUser, core.UserProfile(
              firstName = profileModel.firstName,
              lastName = profileModel.lastName,
              email = profileModel.email,
              description = StrictOption(profileModel.description),
              funFact = StrictOption(profileModel.funFact),
              websites = profileModel.websites,
              twitter = StrictOption(profileModel.twitter),
              linkedIn = StrictOption(profileModel.linkedIn)))

            if (updated) {
              Json(profileModel)
              Ok
            } else {
              Error(Messages("user.admin.profile.saveError"))
            }
          }
        )
    }

  }
}

case class ProfileViewModel(isPublic: Boolean = false,
                            firstName: String = "",
                            lastName: String = "",
                            email: String = "",
                            description: String = "",
                            funFact: String = "",
                            websites: List[String] = List.empty[String],
                            twitter: String = "",
                            linkedIn: String = "",
                            errors: Map[String, String] = Map.empty[String, String]) extends ViewModel

object ProfileViewModel {

  val profileForm = Form(
    mapping(
      "isPublic" -> boolean,
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText,
      "email" -> email,
      "description" -> text,
      "funFact" -> text,
      "websites" -> list(text),
      "twitter" -> text,
      "linkedIn" -> text,
      "errors" -> of[Map[String, String]]
    )(ProfileViewModel.apply)(ProfileViewModel.unapply)
  )

}

