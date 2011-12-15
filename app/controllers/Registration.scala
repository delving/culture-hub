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

import play.data.validation.{Validation, Required, Email}
import play.cache.Cache
import play.libs.Codec
import play.libs.Crypto
import notifiers.Mails
import play.Play
import play.mvc.results.Result
import models.salatContext._
import play.mvc.Controller
import models.{User, Organization, UserCollection, Visibility}
import java.util.Date
import com.mongodb.WriteConcern

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Registration extends Controller with ThemeAware with Internationalization with Logging {

  def index(): Result = {
    Template('randomId -> Codec.UUID())
  }

  def register(): AnyRef = {
    val code = params.get("code")
    val randomId = params.get("randomID")

    Validation.clear()

    // FIXME play is broken !?
//    val r: Registration = params.get("registration", classOf[Registration])
    val r: Registration = Registration(params.get("registration.firstName"), params.get("registration.lastName"), params.get("registration.email"), params.get("registration.userName"), params.get("registration.password1"), params.get("registration.password2"))

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
    if("^[a-z0-9]{3,15}$".r.findFirstIn(r.userName) == None) {
      Validation.addError("registration.userName", "registration.userNameInvalid", r.userName)
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
      val newUser = User(
        userName = r.userName,
        firstName = r.firstName,
        lastName = r.lastName,
        nodes = List(getNode),
        email = r.email,
        password = Crypto.passwordHash(r.password1, Crypto.HashType.SHA512),
        userProfile = models.UserProfile(),
        isActive = false,
        activationToken = Some(activationToken))
      val inserted = User.insert(newUser)

      inserted match {
        case Some(id) =>
          try {
            // create default bookmarks collection
            val bookmarksCollection = UserCollection(
              TS_update = new Date(),
              user_id = id,
              userName =r.userName,
              name = "Bookmarks",
              description = "Bookmarks",
              visibility = Visibility.PRIVATE,
              thumbnail_id = None,
              isBookmarksCollection = Some(true))
            val userCollectionId = UserCollection.insert(bookmarksCollection)
            Mails.activation(newUser, activationToken)
            flash += ("registrationSuccess" -> newUser.email)
            
            try {
              SolrServer.indexSolrDocument(bookmarksCollection.copy(_id = userCollectionId.get).toSolrDocument)
              SolrServer.commit()
            } catch {
              case t => logError(t, "Could not index Bookmarks collection %s for newly created user %s", userCollectionId.get.toString, r.userName)
            }

          } catch {
            case t: Throwable => {
              User.remove(newUser.copy(_id = id))
              logError(t, t.getMessage, r.userName)
              flash += ("registrationError" -> t.getMessage)
            }
          }
        case None =>
          logError("Could not save new user %s", r.userName)
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