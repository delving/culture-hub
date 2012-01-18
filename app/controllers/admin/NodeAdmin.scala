/*
 * Copyright 2012 Delving B.V.
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

package controllers.admin

import controllers.DelvingController
import play.mvc.Before
import play.mvc.results.Result
import models.{User, DObject, UserCollection, Story}
import org.bson.types.ObjectId
import notifiers.Mails

/**
 * NodeAdmin Spot
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object NodeAdmin extends DelvingController {

  @Before
  def checkNodeAdmin(): Result = {
    if (!isNodeAdmin) {
      reportSecurity("User %s tried to get access to node administration".format(connectedUser))
      return Forbidden(&("user.secured.noAccess"))
    }
    Continue
  }

  def blockUser(user: String): Result = {
    val userObject = User.findByUsername(user).getOrElse(return Error("User %s not found".format(user)))
    val adminEmail = User.findByUsername(connectedUser).get.email
    User.block(user, connectedUser)
    Mails.accountBlocked(userObject, adminEmail)
    Ok
  }

  def blockDObject(id: ObjectId): Result = {
    DObject.block(id, connectedUser)
    // TODO notify user
    Ok
  }

  def blockUserCollection(id: ObjectId): Result = {
    UserCollection.block(id, connectedUser)
    // TODO notify user
    Ok
  }

  def blockStory(id: ObjectId): Result = {
    Story.block(id, connectedUser)
    // TODO notify user
    Ok
  }

}