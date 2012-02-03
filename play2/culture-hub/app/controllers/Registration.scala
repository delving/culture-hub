/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import notifiers.Mails
import play.api.Play.current
import models.mongoContext._
import models.{User, Organization}
import util.MissingLibs
import play.api._
import cache.Cache
import data.Form._
import http.{ContentTypeOf, ContentTypes, Writeable}
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import validation.{ValidationError, Valid, Invalid, Constraint}
import play.libs.Time
import play.libs.Images.Captcha

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Registration extends ApplicationController {

  case class RegistrationInfo(firstName: String,
                              lastName: String,
                              email: String,
                              userName: String,
                              password1: String,
                              password2: String,
                              code: String,
                              randomId: String)

  val samePassword = Constraint[RegistrationInfo]("registration.passwordsDiffer") {
    case r if r.password1 == r.password2 => Valid
    case _ => Invalid(ValidationError(Messages("registration.passwordsDiffer")))
  }

  val captchaConstraint = Constraint[RegistrationInfo]("registration.invalidCode") {
    case r if Cache.get(r.randomId) == Some(r.code) => Valid
    case e => Invalid(ValidationError(Messages("registration.invalidCode")))
  }

  val emailTaken = Constraint[RegistrationInfo]("registration.duplicateEmail") {
    case r if !User.existsWithEmail(r.email) => Valid
    case _ => Invalid(ValidationError(Messages("registration.duplicateEmail")))
  }

  val userNameTaken = Constraint[RegistrationInfo]("registration.duplicateDisplayName") {
    case r if !User.existsWithUsername(r.userName) => Valid
    case _ => Invalid(ValidationError(Messages("registration.duplicateDisplayName")))
  }

  val orgIdTaken = Constraint[RegistrationInfo]("registration.duplicateDisplayName") {
    case r if Organization.findByOrgId(r.userName).isEmpty => Valid
    case _ => Invalid(ValidationError(Messages("registration.duplicateDisplayName")))
  }

  val registrationForm: Form[RegistrationInfo] = Form(
    mapping(
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText,
      "email" -> email,
      "userName" -> text.verifying(pattern("^[a-z0-9]{3,15}$".r, error = Messages("registration.userNameInvalid"))),
      "password1" -> nonEmptyText,
      "password2" -> nonEmptyText,
      "code" -> text,
      "randomId" -> text
    )(RegistrationInfo.apply)(RegistrationInfo.unapply).verifying(samePassword, captchaConstraint, emailTaken, userNameTaken, orgIdTaken)

  )

  def index() = Themed {
    Action {
      implicit request =>
        Ok(Template('randomId -> MissingLibs.UUID, 'registrationForm -> registrationForm))
    }
  }

  def register() = Themed {
    Action {
      implicit request =>
        registrationForm.bindFromRequest.fold(
          formWithErrors => {
            registrationForm.value.map(r => Cache.set(r.randomId, null))
            BadRequest(Template("/Registration/index.html", 'randomId -> MissingLibs.UUID, 'registrationForm -> formWithErrors))
          },
          registration => {
            val r = registration
            Cache.set(r.randomId, null)

            val activationToken: String = if (Play.isTest) "testActivationToken" else MissingLibs.UUID
            val newUser = User(
              userName = r.userName,
              firstName = r.firstName,
              lastName = r.lastName,
              nodes = List(getNode),
              email = r.email,
              password = MissingLibs.passwordHash(r.password1, MissingLibs.HashType.SHA512),
              userProfile = models.UserProfile(),
              isActive = false,
              activationToken = Some(activationToken))

            val inserted = User.insert(newUser)

            val index = Redirect(controllers.routes.Application.index)

            inserted match {
              case Some(id) =>
                try {
                  Mails.activation(newUser, activationToken, theme)
                  index.flashing(("registrationSuccess", newUser.email))
                } catch {
                  case t => {
                    User.remove(newUser.copy(_id = id))
                    logError(t, t.getMessage, r.userName)
                    index.flashing(("registrationError" -> t.getMessage))
                  }
                }
              case None =>
                logError("Could not save new user %s", r.userName)
                index.flashing(("registrationError" -> Messages("registration.errorCreating")))
            }
          }
        )
    }
  }

  implicit val contentTypeOf_captcha = ContentTypeOf[Captcha](Some("image/png"))

  // there must be a way to stream this tough
  implicit val wCaptcha: Writeable[Captcha] = Writeable[Captcha](c => Stream.continually(c.read).takeWhile(-1 !=).map(_.toByte).toArray)

  def captcha(id: String) = Action {
    implicit request =>
      val captcha = play.libs.Images.captcha
      val code = captcha.getText("#000000")
      Cache.set(id, code, Time.parseDuration("10mn"))
      Ok(captcha)
  }
  
  def activate(activationToken: String) = Themed {
    Action {
      implicit request =>
        val indexAction = Redirect(controllers.routes.Application.index)
        if(Option(activationToken).isEmpty) {
          warning("Empty activation token received")
          indexAction.flashing(("activation", "false"))
        } else {
          val activated = User.activateUser(activationToken)
          if (activated.isDefined) {
            try {
              Mails.newUser("New user registered on " + getNode, getNode, activated.get.userName, activated.get.fullname, activated.get.email, theme)
              indexAction.flashing(("activation", "true"))
            } catch {
              case t => logError(t, "Could not send activation email")
              indexAction.flashing(("activation", "false"))
            }
          } else {
            indexAction.flashing(("activation", "false"))
          }
        }
    }
  }
  
  def lostPassword = Themed {
    Action {
      implicit request => Ok(Template('resetPasswordForm -> resetPasswordForm))
    }
  }


  val accountNotFound = Constraint[String]("registration.accountNotFoundWithEmail") {
    case r if User.findByEmail(r).isDefined => Valid
    case _ => Invalid(ValidationError(Messages("registration.accountNotFoundWithEmail")))
  }
  
  val accountNotActive = Constraint[String]("registration.accountNotActive") {
    case r if User.findByEmail(r).map(_.isActive).getOrElse(false) => Valid
    case _ => Invalid(ValidationError(Messages("registration.accountNotActive")))
  }

  case class ResetPassword(email: String)

  val resetPasswordForm: Form[ResetPassword] = Form(
    mapping(
      "email" -> email.verifying(accountNotFound, accountNotActive)
    )(ResetPassword.apply)(ResetPassword.unapply)
  )

  def resetPasswordEmail = Themed {
    Action {
      implicit request =>
        resetPasswordForm.bindFromRequest().fold(
          formWithErrors => BadRequest(Template("Registration/lostPassword.html", 'resetPasswordForm -> formWithErrors)),
          resetPassword => {
            val resetPasswordToken = if (Play.isTest) "testResetPasswordToken" else MissingLibs.UUID
            val u = User.findByEmail(resetPassword.email).get
            User.preparePasswordReset(u, resetPasswordToken)
            Mails.resetPassword(u, resetPasswordToken, theme)
            Redirect(controllers.routes.Application.index).flashing(("resetPasswordEmail", "true"))
          }
        )

    }
  }

  def resetPassword(resetPasswordToken: String) = Themed {
    Action {
      implicit request =>
        val indexAction = Redirect(controllers.routes.Application.index)
        if(Option(resetPasswordToken).isEmpty) {
          indexAction.flashing(("resetPasswordError", Messages("registration.resetTokenNotFound")))
        } else {
          if(User.canChangePassword(resetPasswordToken)) {
            indexAction.flashing(("resetPasswordError", Messages("registration.errorPasswordChange")))
          } else {
            Ok(Template('resetPasswordToken -> resetPasswordToken, 'newPasswordForm -> newPasswordForm))
          }
        }
    }
  }

  val sameNewPassword = Constraint[NewPassword]("registration.passwordsDiffer") {
    case r if r.password1 == r.password2 => Valid
    case _ => Invalid(ValidationError(Messages("registration.passwordsDiffer")))
  }

  case class NewPassword(password1: String, password2: String)

  val newPasswordForm: Form[NewPassword] = Form(
    mapping(
      "password1" -> nonEmptyText,
      "password2" -> nonEmptyText
    )(NewPassword.apply)(NewPassword.unapply) verifying(sameNewPassword)
  )

  def newPassword(resetPasswordToken: String) = Themed {
    Action {
      implicit request =>
        if(Option(resetPasswordToken).isEmpty) {
          Redirect(controllers.routes.Application.index).flashing(("resetPasswordSuccess", "false"))
        } else {
          newPasswordForm.bindFromRequest().fold(
            formWithErrors => BadRequest(Template("Registration/resetPassword.html", 'newPasswordForm -> formWithErrors)),
            newPassword => {
              User.changePassword(resetPasswordToken, newPassword.password1)
              Redirect(controllers.routes.Application.index).flashing(("resetPasswordSuccess", "true"))
            }
          )
        }
    }
  }

}