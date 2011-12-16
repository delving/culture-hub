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

package controllers.user

import play.mvc.results.Result
import extensions.JJson
import models.{UserProfile, User}
import play.data.validation.Annotations._
import controllers.{ViewModel, DelvingController}
import play.data.validation.Validation

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object Admin extends DelvingController with UserSecured {

  def index: Result = Template

  def profile: Result = {
    val u = User.findByUsername(connectedUser).get
    val p = u.userProfile
    val profile = ProfileViewModel(p.isPublic, u.firstName, u.lastName, u.email, p.description.getOrElse(""), p.funFact.getOrElse(""), p.websites, p.twitter.getOrElse(""), p.linkedIn.getOrElse(""))
    Template('data -> JJson.generate(profile), 'viewModel -> classOf[ProfileViewModel])
  }

  def profileSubmit(data: String): Result = {
    val profileModel = JJson.parse[ProfileViewModel](data)

    if(!Validation.url("linkedIn", profileModel.linkedIn).ok) {
      Validation.addError("object.linkedIn", "validation.url")
    }

    validate(profileModel) match {
      case Some(errors) => return JsonBadRequest(profileModel.copy(errors = errors))
      case None => // happy
    }

    def StrictOption(s: String) = s match {
      case s if s != null && s.trim.length() > 0 => Some(s)
      case _ => None
    }

    val updated = User.updateProfile(connectedUser, profileModel.firstName, profileModel.lastName, profileModel.email,
      UserProfile(
        isPublic = profileModel.isPublic,
        description = StrictOption(profileModel.description),
        funFact = StrictOption(profileModel.funFact),
        websites = profileModel.websites,
        twitter = StrictOption(profileModel.twitter),
        linkedIn = StrictOption(profileModel.linkedIn)))
    if(updated) {
      Json(data)
    } else {
      Error(&("user.admin.profile.saveError"))
    }
  }

}

case class ProfileViewModel(isPublic: Boolean = false,
                            @Required firstName: String = "",
                            @Required lastName: String = "",
                            @Required @Email email: String = "",
                            description: String = "",
                            funFact: String = "",
                            websites: List[String] = List.empty[String],
                            twitter: String = "",
                            linkedIn: String = "",
                            errors: Map[String,  String] = Map.empty[String, String]) extends ViewModel