package models

import com.novus.salat._
import com.mongodb.casbah.Imports._
import dao.SalatDAO
import models.salatContext._
import controllers.InactiveUserException
import play.libs.Crypto

case class User(firstName: String,
                lastName: String,
                email: String,
                password: String,
                displayName: String,
                isActive: Boolean = false,
                activationToken: Option[String] = None,
                resetPasswordToken: Option[String] = None,
                isAdmin: Boolean = false) {
  val fullname = firstName + " " + lastName
}

object User extends SalatDAO[User, ObjectId](collection = userCollection) {

  val nobody: User = User("", "", "none@nothing.com", "", "Nobody", false, None, None, false)

  def connect(email: String, password: String): Boolean = {
    val theOne: Option[User] = User.findOne(MongoDBObject("email" -> email, "password" -> Crypto.passwordHash(password)))
    if (!theOne.getOrElse(return false).isActive) {
      throw new InactiveUserException
    }
    true
  }

  def findByEmail(email: String) = User.findOne(MongoDBObject("email" -> email))

  def existsWithEmail(email: String) = User.count(MongoDBObject("email" -> email)) != 0

  def existsWithDisplayName(displayName: String) = User.count(MongoDBObject("displayName" -> displayName)) != 0

  def activateUser(activationToken: String): Boolean = {
    val user: User = User.findOne(MongoDBObject("activationToken" -> activationToken)) getOrElse (return false)
    val activeUser: User = user.copy(isActive = true, activationToken = None)
    User.update(MongoDBObject("email" -> activeUser.email), activeUser, false, false, new WriteConcern())
    // also log the guy in
    play.mvc.Scope.Session.current().put("username", activeUser.email)
    true
  }

  def preparePasswordReset(user: User, resetPasswordToken: String) {
    val resetUser = user.copy(resetPasswordToken = Some(resetPasswordToken))
    User.update(MongoDBObject("email" -> resetUser.email), resetUser, false, false, new WriteConcern())
  }

  def canChangePassword(resetPasswordToken: String): Boolean = User.count(MongoDBObject("resetPasswordToken" -> resetPasswordToken)) != 0

  def findByResetPasswordToken(resetPasswordToken: String): Option[User] = User.findOne(MongoDBObject("resetPasswordToken" -> resetPasswordToken))

  def changePassword(resetPasswordToken: String, newPassword: String): Boolean = {
    val user = findByResetPasswordToken(resetPasswordToken).getOrElse(return false)
    val resetUser = user.copy(password = Crypto.passwordHash(newPassword), resetPasswordToken = None)
    User.update(MongoDBObject("resetPasswordToken" -> resetPasswordToken), resetUser, false, false, new WriteConcern())
    true
  }
}