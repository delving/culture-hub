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

package models

import util.MissingLibs
import util.Constants._
import com.novus.salat._
import com.novus.salat.dao._
import com.mongodb.casbah.Imports._
import mongoContext._
import play.Play
import core.indexing.IndexingService

case class User(_id: ObjectId = new ObjectId,
                userName: String,                                 // userName, unique in the world
                firstName: String,
                lastName: String,
                email: String,
                password: String,
                userProfile: UserProfile,
                groups: List[ObjectId] = List.empty[ObjectId],    // groups this user belongs to
                organizations: List[String] = List.empty[String], // organizations this user belongs to
                nodes: List[String] = List.empty[String],         // nodes this user has access to
                nodesAdmin: List[String] = List.empty[String],    // nodes this user is an admin of
                isActive: Boolean = false,
                blocked: Boolean = false,
                blockingInfo: Option[BlockingInfo] = None,
                activationToken: Option[String] = None,
                resetPasswordToken: Option[ResetPasswordToken] = None,
                accessToken: Option[AccessToken] = None,
                refreshToken: Option[String] = None,
                isHubAdmin: Option[Boolean] = None) {             // super-user powers everywhere

  val fullname = firstName + " " + lastName

  override def toString = email
}

case class UserProfile(isPublic: Boolean = false,
                   description: Option[String] = None,
                   funFact: Option[String] = None,
                   // place: EmbeddedLink, // TODO
                   websites: List[String] = List.empty[String],
                   twitter: Option[String] = None,
                   facebook: Option[String] = None,
                   linkedIn: Option[String] = None)

/** User password reset token **/
case class ResetPasswordToken(token: String, issueTime: Long = System.currentTimeMillis())

/** OAuth2 Access token **/
case class AccessToken(token: String, issueTime: Long = System.currentTimeMillis())

object User extends SalatDAO[User, ObjectId](userCollection) with Pager[User] {

  val PASSWORD_RESET_TOKEN_EXPIRATION = 3600 //ms

  val nobody: User = User(userName = "", firstName = "", lastName = "", email = "none@nothing.com", password = "", isActive = false, userProfile = UserProfile())

  def connect(userName: String, password: String): Boolean = {
    val theOne: Option[User] = User.findOne(MongoDBObject("userName" -> userName, "password" -> MissingLibs.passwordHash(password, MissingLibs.HashType.SHA512)))
    if (!theOne.getOrElse(return false).isActive) {
      return false
    }
    if(theOne.getOrElse(return false).blocked) {
      return false
    }
    true
  }

  // ~~~ global finders

  def findAll = find(MongoDBObject("isActive" -> true))

  def findByEmail(email: String) = User.findOne(MongoDBObject("email" -> email))

  def findByUsername(userName: String, active: Boolean = true) = User.findOne(MongoDBObject("userName" -> userName, "isActive" -> active))

  // ~~~ profile

  def updateProfile(userName: String, firstName: String, lastName: String, email: String, profile: UserProfile): Boolean = {
    val p = grater[UserProfile].asDBObject(profile)
    try {
      User.update(MongoDBObject("userName" -> userName), $set("userProfile" -> p, "firstName" -> firstName, "lastName" -> lastName, "email" -> email), false, false, WriteConcern.Safe)
      true
    } catch {
      case s: SalatDAOError => false
      case _ => false
    }
  }

  def block(userName: String, whoBlocks: String) {
    User.findByUsername(userName) map {
      u =>
        val updated = u.copy(blocked = true, blockingInfo = Some(BlockingInfo(whoBlocks)))
        User.save(updated)

        val blockedObjects = DObject.findByUser(userName).map(_._id).toList
        val blockedCollections = UserCollection.findByUser(userName).map(_._id).toList
        val blockedStories = Story.findByUser(userName).map(_._id).toList

        DObject.update(MongoDBObject("userName" -> userName), $set ("blocked" -> true, "blockingInfo" -> grater[BlockingInfo].asDBObject(BlockingInfo(whoBlocks))), false, true)
        UserCollection.update(MongoDBObject("userName" -> userName), $set ("blocked" -> true, "blockingInfo" -> grater[BlockingInfo].asDBObject(BlockingInfo(whoBlocks))), false, true)
        Story.update(MongoDBObject("userName" -> userName), $set ("blocked" -> true, "blockingInfo" -> grater[BlockingInfo].asDBObject(BlockingInfo(whoBlocks))), false, true)

        IndexingService.deleteById(blockedObjects ++ blockedCollections ++ blockedStories)

        blockedObjects foreach { o =>
          Link.blockLinks(OBJECT, o, whoBlocks)
        }

        blockedCollections foreach { c =>
          Link.blockLinks(USERCOLLECTION, c, whoBlocks)
        }

        blockedStories foreach { s =>
          Link.blockLinks(STORY, s, whoBlocks)
        }

    }

  }

  // ~~~ user registration, password reset

  def existsWithEmail(email: String) = User.count(MongoDBObject("email" -> email)) != 0

  def existsWithUsername(userName: String) = User.count(MongoDBObject("userName" -> userName)) != 0

  def activateUser(activationToken: String): Option[User] = {
    val user: User = User.findOne(MongoDBObject("activationToken" -> activationToken)) getOrElse (return None)
    val activeUser: User = user.copy(isActive = true, activationToken = None)
    User.update(MongoDBObject("userName" -> activeUser.userName), activeUser, false, false, new WriteConcern())

    // TODO MIGRATION - move this to the controller
  // also log the guy in
    // play.mvc.Scope.Session.current().put("userName", activeUser.userName)
    // new ServicesSecurity().onAuthenticated(activeUser.userName, play.mvc.Scope.Session.current())
    Some(user)
  }

  def preparePasswordReset(user: User, resetPasswordToken: String) {
    val resetUser = user.copy(resetPasswordToken = Some(ResetPasswordToken(resetPasswordToken)))
    User.update(MongoDBObject("userName" -> resetUser.userName), resetUser, false, false, new WriteConcern())
  }

  def canChangePassword(resetPasswordToken: String): Boolean = User.count(MongoDBObject("resetPasswordToken.token" -> resetPasswordToken)) != 0

  def findByResetPasswordToken(resetPasswordToken: String): Option[User] = {
    val delta = System.currentTimeMillis() - PASSWORD_RESET_TOKEN_EXPIRATION * 1000
    User.findOne(MongoDBObject("resetPasswordToken.token" -> resetPasswordToken) ++ ("resetPasswordToken.issueTime" $lt delta))
  }

  def changePassword(resetPasswordToken: String, newPassword: String): Boolean = {
    val user = findByResetPasswordToken(resetPasswordToken).getOrElse(return false)
    val resetUser = user.copy(password = MissingLibs.passwordHash(newPassword, MissingLibs.HashType.SHA512), resetPasswordToken = None)
    User.update(MongoDBObject("resetPasswordToken.token" -> resetPasswordToken), resetUser, false, false, new WriteConcern())
    true
  }

  def findBookmarksCollection(userName: String): Option[UserCollection] = {
    UserCollection.findOne(MongoDBObject("isBookmarksCollection" -> true, "userName" -> userName))
  }

  def evictExpiredPasswordResetTokens(timeout: Long = 3600) {
    val delta = System.currentTimeMillis() - timeout * 1000
    User.update("resetPasswordToken.issueTime" $lt delta, $unset ("resetPasswordToken"), false, false, new WriteConcern())
  }


  // ~~~ OAuth2

  def setOauthTokens(user: User, accessToken: String, refreshToken: String) {
    User.update(MongoDBObject("userName" -> user.userName), user.copy(accessToken = Some(AccessToken(token = accessToken)), refreshToken = Some(refreshToken)), false, false, new WriteConcern())
  }

  def isValidAccessToken(token: String, timeout: Long = 3600): Boolean = {
    val delta = System.currentTimeMillis() - timeout * 1000
    User.count(MongoDBObject("accessToken.token" -> token, "accessToken.issueTime" -> MongoDBObject("$gt" -> delta))) > 0
  }

  def findByAccessToken(token: String): Option[User] = {
    if(Play.isTest && token == "TEST" || Play.isTest) return User.findOne(MongoDBObject("userName" -> "bob"))
    User.findOne(MongoDBObject("accessToken.token" -> token))
  }

  def findByRefreshToken(token: String): Option[User] = {
    User.findOne(MongoDBObject("refreshToken" -> token))
  }

  def evictExpiredAccessTokens(timeout: Long = 3600) {
    val delta = System.currentTimeMillis() - timeout * 1000
    User.update("accessToken.issueTime" $lt delta, MongoDBObject("$unset" -> MongoDBObject("accessToken" -> 1)), false, false, new WriteConcern())
  }


}