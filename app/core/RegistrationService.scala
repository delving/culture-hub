package core

/**
 * Registration of new users, password reset
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait RegistrationService {

  def isUserNameTaken(userName: String): Boolean

  def isEmailTaken(email: String): Boolean

  /**
   * Registers a user with the service. If things are good, returns an activationToken to be given to the user for verification
   */
  def registerUser(userName: String, node: String, firstName: String, lastName: String, email: String, password: String): Option[String]

  def activateUser(activationToken: String): Option[RegisteredUser]

  /** Prepares password reset by returning a reset token **/
  def preparePasswordReset(userName: String): Option[String]

  def resetPassword(resetToken: String, newPassword: String): Boolean

}

case class RegisteredUser(userName: String, firstName: String, lastName: String, email: String) {
  val fullName = firstName + " " + lastName
}


