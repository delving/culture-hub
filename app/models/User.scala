package models

import com.novus.salat._
import com.mongodb.casbah.Imports._
import dao.SalatDAO
import models.salatContext._
import controllers.InactiveUserException
import play.libs.Crypto

/** Unique reference to a user across the CultureHub space **/
case class UserReference(username: String = "", node: String = "", id: String = "")

object UserReference extends SalatDAO[UserReference, ObjectId](collection = userCollection)

case class User(reference: UserReference,
                firstName: String,
                lastName: String,
                email: String,
                password: String,
                displayName: String,
                isActive: Boolean = false,
                activationToken: Option[String] = None,
                resetPasswordToken: Option[String] = None) {
  val fullname = firstName + " " + lastName
}

object User extends SalatDAO[User, ObjectId](collection = userCollection) {

  val nobody: User = User(UserReference("", "", "") ,"", "", "none@nothing.com", "", "Nobody", false, None, None)

  def connect(username: String, password: String, node: String): Boolean = {
    val theOne: Option[User] = User.findOne(MongoDBObject("reference.username" -> username, "reference.node" -> node, "password" -> Crypto.passwordHash(password)))
    if (!theOne.getOrElse(return false).isActive) {
      throw new InactiveUserException
    }
    true
  }

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
    play.mvc.Scope.Session.current().put("username", activeUser.displayName)
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
}