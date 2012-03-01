package core.services

import models.User
import com.mongodb.casbah.Imports._
import extensions.MissingLibs
import org.bson.types.ObjectId
import play.api.Play.current
import core.{RegisteredUser, RegistrationService}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class MongoRegistrationService extends RegistrationService {

  def isUserNameTaken(userName: String) = User.existsWithUsername(userName)

  def isEmailTaken(email: String) = User.existsWithEmail(email)


  def isAccountActive(email: String) = User.findByEmail(email).map(u => u.isActive).getOrElse(false)

  def registerUser(userName: String, node: String, firstName: String, lastName: String, email: String, password: String) = {
    val newUser = User(
      userName = userName,
      firstName = firstName,
      lastName = lastName,
      nodes = List(node),
      email = email,
      password = MissingLibs.passwordHash(password, MissingLibs.HashType.SHA512),
      userProfile = models.UserProfile(),
      isActive = false
    )

    User.insert(newUser) map {
      id => id.toString
    }
  }

  def activateUser(activationToken: String) = {
    if(!ObjectId.isValid(activationToken)) {
      None
    } else {
      User.findOneByID(new ObjectId(activationToken)) map {
        user =>
          User.update(MongoDBObject("_id" -> user._id), $set ("isActive" -> true), false, false)
          RegisteredUser(user.userName, user.firstName, user.lastName, user.email)
      }
    }
  }

  def preparePasswordReset(email: String) = {
    val resetPasswordToken = if (play.api.Play.isTest) "testResetPasswordToken" else MissingLibs.UUID
    User.preparePasswordReset(email, resetPasswordToken)
    Some(resetPasswordToken)
  }

  def resetPassword(resetPasswordToken: String, newPassword: String) = User.changePassword(resetPasswordToken, newPassword)

}
