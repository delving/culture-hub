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
import core.{ UserProfileService, DomainServiceLocator, HubModule }
import com.escalatesoft.subcut.inject.BindingModule

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class Admin(implicit val bindingModule: BindingModule) extends DelvingController {

  val userProfileServiceLocator = inject[DomainServiceLocator[UserProfileService]]

  def profile(user: String) = SecuredUserAction(user) {
    MultitenantAction {
      implicit request =>
        val u = HubUser.dao.findByUsername(connectedUser).get
        val p = u.userProfile
        val profile = ProfileViewModel(
          p.isPublic,
          u.firstName,
          u.lastName,
          u.email,
          p.fixedPhone.getOrElse(""),
          p.description.getOrElse(""),
          p.funFact.getOrElse(""),
          p.websites,
          p.twitter.getOrElse(""),
          p.linkedIn.getOrElse("")
        )
        Ok(Template('data -> JJson.generate(profile), 'profileForm -> ProfileViewModel.profileForm))
    }
  }

  def profileSubmit(user: String) = SecuredUserAction(user) {
    MultitenantAction {
      implicit request =>
        ProfileViewModel.profileForm.bind(request.body.asJson.get).fold(
          formWithErrors => handleValidationError(formWithErrors),
          profileModel => {
            def StrictOption(s: String) = Option(s).filter(e => e.trim.length > 0)

            // update local
            HubUser.dao.updateProfile(connectedUser, profileModel.firstName, profileModel.lastName, profileModel.email, UserProfile(
              isPublic = profileModel.isPublic,
              description = StrictOption(profileModel.description),
              fixedPhone = StrictOption(profileModel.fixedPhone),
              funFact = StrictOption(profileModel.funFact),
              websites = profileModel.websites,
              twitter = StrictOption(profileModel.twitter),
              linkedIn = StrictOption(profileModel.linkedIn
              )))

            // update remote
            val updated = userProfileServiceLocator.byDomain.updateUserProfile(connectedUser, core.UserProfile(
              isPublic = profileModel.isPublic,
              firstName = profileModel.firstName,
              lastName = profileModel.lastName,
              email = profileModel.email,
              fixedPhone = StrictOption(profileModel.fixedPhone),
              description = StrictOption(profileModel.description),
              funFact = StrictOption(profileModel.funFact),
              websites = profileModel.websites,
              twitter = StrictOption(profileModel.twitter),
              linkedIn = StrictOption(profileModel.linkedIn)))

            if (updated) {
              Json(profileModel)
              Ok
            } else {
              Json(Map("errors" -> Map("global" -> Messages("hub.ErrorSavingYourProfile"))), BAD_REQUEST)
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
  fixedPhone: String = "",
  description: String = "",
  funFact: String = "",
  websites: List[String] = List.empty[String],
  twitter: String = "",
  linkedIn: String = "")

object ProfileViewModel {

  val profileForm = Form(
    mapping(
      "isPublic" -> boolean,
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText,
      "email" -> email,
      "fixedPhone" -> text,
      "description" -> text,
      "funFact" -> text,
      "websites" -> list(text),
      "twitter" -> text,
      "linkedIn" -> text
    )(ProfileViewModel.apply)(ProfileViewModel.unapply)
  )

}