package models

import com.novus.salat._
import com.mongodb.casbah.Imports._
import dao.SalatDAO
import models.salatContext._
import controllers.InactiveUserException
import play.libs.Crypto

case class User(_id: ObjectId = new ObjectId,
                userName: String, // userName, unique in the world
                firstName: String,
                lastName: String,
                email: String,
                password: String,
                groups: List[ObjectId] = List.empty[ObjectId], // groups this user belongs to
                organizations: List[ObjectId] = List.empty[ObjectId], // organizations this user belongs to
                nodes: List[String] = List.empty[String], // nodes this user has access to
                isActive: Boolean = false,
                activationToken: Option[String] = None,
                resetPasswordToken: Option[String] = None,
                accessToken: Option[AccessToken] = None,
                refreshToken: Option[String] = None) {
  val fullname = firstName + " " + lastName

  override def toString = email
}

/** OAuth2 Access token **/
case class AccessToken(token: String, issueTime: Long = System.currentTimeMillis())

object User extends SalatDAO[User, ObjectId](userCollection) with Pager[User] {

  val nobody: User = User(userName = "", firstName = "", lastName = "", email = "none@nothing.com", password = "", isActive = false)

  def connect(userName: String, password: String): Boolean = {
    val theOne: Option[User] = User.findOne(MongoDBObject("userName" -> userName, "password" -> Crypto.passwordHash(password)))
    if (!theOne.getOrElse(return false).isActive) {
      throw new InactiveUserException
    }
    true
  }

  def findAll = find(MongoDBObject("isActive" -> true))

  def findByEmail(email: String) = User.findOne(MongoDBObject("email" -> email))

  def findByUsername(userName: String, active: Boolean = true) = User.findOne(MongoDBObject("userName" -> userName, "isActive" -> active))

  def existsWithEmail(email: String) = User.count(MongoDBObject("email" -> email)) != 0

  def existsWithUsername(userName: String) = User.count(MongoDBObject("userName" -> userName)) != 0

  def activateUser(activationToken: String): Boolean = {
    val user: User = User.findOne(MongoDBObject("activationToken" -> activationToken)) getOrElse (return false)
    val activeUser: User = user.copy(isActive = true, activationToken = None)
    User.update(MongoDBObject("userName" -> activeUser.userName), activeUser, false, false, new WriteConcern())
    // also log the guy in
    play.mvc.Scope.Session.current().put("username", activeUser.userName)
    true
  }

  def preparePasswordReset(user: User, resetPasswordToken: String) {
    val resetUser = user.copy(resetPasswordToken = Some(resetPasswordToken))
    User.update(MongoDBObject("userName" -> resetUser.userName), resetUser, false, false, new WriteConcern())
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
    User.update(MongoDBObject("userName" -> user.userName), user.copy(accessToken = Some(AccessToken(token = accessToken)), refreshToken = Some(refreshToken)), false, false, new WriteConcern())
  }

  def isValidAccessToken(token: String, timeout: Long = 3600): Boolean = {
    val delta = System.currentTimeMillis() - timeout * 1000
    User.count(MongoDBObject("accessToken.token" -> token, "accessToken.issueTime" -> MongoDBObject("$gt" -> delta))) > 0
  }

  def findByAccessToken(token: String): Option[User] = {
    if(play.Play.mode == play.Play.Mode.DEV && token == "TEST") return User.findOne(MongoDBObject("userName" -> "bob"))
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