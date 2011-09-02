package models

import com.novus.salat._
import com.mongodb.casbah.Imports._
import dao.SalatDAO
import models.salatContext._
import controllers.InactiveUserException
import play.libs.Crypto

case class User(_id: ObjectId = new ObjectId,
                reference: UserReference = UserReference("", "", ""),
                firstName: String,
                lastName: String,
                email: String,
                password: String,
                isActive: Boolean = false,
                activationToken: Option[String] = None,
                resetPasswordToken: Option[String] = None,
                accessToken: Option[AccessToken] = None,
                refreshToken: Option[String] = None) {
  val fullname = firstName + " " + lastName
}

/** Unique reference to a user across the CultureHub space. This is useful to lookup users based on their username **/
case class UserReference(username: String = "", node: String = "", id: String = "")

/** OAuth2 Access token **/
case class AccessToken(token: String, issueTime: Long = System.currentTimeMillis())

object User extends SalatDAO[User, ObjectId](userCollection) {

  val nobody: User = User(reference = UserReference("", "", ""), firstName = "", lastName = "", email = "none@nothing.com", password = "", isActive = false)

  def connect(username: String, password: String, node: String): Boolean = {
    val theOne: Option[User] = User.findOne(MongoDBObject("reference.username" -> username, "reference.node" -> node, "password" -> Crypto.passwordHash(password)))
    if (!theOne.getOrElse(return false).isActive) {
      throw new InactiveUserException
    }
    true
  }

  def findAllIdName: List[DBObject] = User.collection.find(MongoDBObject(), MongoDBObject("reference.id" -> 1, "firstName" -> 1, "lastName" -> 1)).toList

  def findByEmail(email: String) = User.findOne(MongoDBObject("email" -> email))

  def findByUsername(username: String, node: String) = User.findOne(MongoDBObject("reference.username" -> username, "reference.node" -> node))

  def findByUserId(id: String) = User.findOne(MongoDBObject("reference.id" -> id))

  def existsWithEmail(email: String) = User.count(MongoDBObject("displayName" -> email)) != 0

  def existsWithUsername(displayName: String, node: String) = User.count(MongoDBObject("reference.username" -> displayName, "reference.node" -> node)) != 0

  def activateUser(activationToken: String): Boolean = {
    val user: User = User.findOne(MongoDBObject("activationToken" -> activationToken)) getOrElse (return false)
    val activeUser: User = user.copy(isActive = true, activationToken = None)
    User.update(MongoDBObject("reference.id" -> activeUser.reference.id), activeUser, false, false, new WriteConcern())
    // also log the guy in
    play.mvc.Scope.Session.current().put("username", activeUser.reference.username)
    true
  }

  def preparePasswordReset(user: User, resetPasswordToken: String) {
    val resetUser = user.copy(resetPasswordToken = Some(resetPasswordToken))
    User.update(MongoDBObject("reference.id" -> resetUser.reference.id), resetUser, false, false, new WriteConcern())
  }

  def canChangePassword(resetPasswordToken: String): Boolean = User.count(MongoDBObject("resetPasswordToken" -> resetPasswordToken)) != 0

  def findByResetPasswordToken(resetPasswordToken: String): Option[User] = User.findOne(MongoDBObject("resetPasswordToken" -> resetPasswordToken))

  def changePassword(resetPasswordToken: String, newPassword: String): Boolean = {
    val user = findByResetPasswordToken(resetPasswordToken).getOrElse(return false)
    val resetUser = user.copy(password = Crypto.passwordHash(newPassword), resetPasswordToken = None)
    User.update(MongoDBObject("resetPasswordToken" -> resetPasswordToken), resetUser, false, false, new WriteConcern())
    true
  }

  def setOauthTokens(user: User, accessToken: String, refreshToken: String) {
    User.update(MongoDBObject("reference.id" -> user.reference.id), user.copy(accessToken = Some(AccessToken(token = accessToken)), refreshToken = Some(refreshToken)), false, false, new WriteConcern())
  }

  def isValidAccessToken(token: String, timeout: Long = 3600): Boolean = {
    val delta = System.currentTimeMillis() - timeout * 1000
    User.count(MongoDBObject("accessToken.token" -> token, "accessToken.issueTime" -> MongoDBObject("$gt" -> delta))) > 0
  }

  def findByAccessToken(token: String): Option[User] = {
    if(play.Play.mode == play.Play.Mode.DEV && token == "TEST") return User.findOne(MongoDBObject("reference.username" -> "bob"))
    User.findOne(MongoDBObject("accessToken.token" -> token))
  }

  def findByRefreshToken(token: String): Option[User] = {
    User.findOne(MongoDBObject("refreshToken" -> token))
  }

  def evictExpiredAccessTokens(timeout: Long = 3600) {
    val delta = System.currentTimeMillis() - timeout * 1000
    User.update(MongoDBObject("accessToken.issueTime" -> MongoDBObject("$lt" -> delta)), MongoDBObject("$unset" -> MongoDBObject("accessToken" -> 1)), false, false, new WriteConcern())
  }
}