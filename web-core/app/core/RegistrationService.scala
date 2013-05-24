package core

/**
 * Registration of new users, password reset
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait RegistrationService {

  /**
   * Whether this userName is already taken
   */
  def isUserNameTaken(userName: String): Boolean

  /**
   * Whether this email is already used
   */
  def isEmailTaken(email: String): Boolean

  /**
   * Whether this user account is acitve
   */
  def isAccountActive(email: String): Boolean

  /**
   * Registers a user with the service. If things go well, returns an activationToken to be given to the user for verification
   */
  def registerUser(userName: String, node: String, firstName: String, lastName: String, email: String, password: String): Option[String]

  /**
   * Actives a user given an activationToken
   */
  def activateUser(activationToken: String): Option[RegisteredUser]

  /**
   * Prepares password reset by returning a reset token
   */
  def preparePasswordReset(email: String): Option[String]

  /**
   * Set a new password for the user
   */
  def resetPassword(resetToken: String, newPassword: String): Boolean

}

case class RegisteredUser(userName: String, firstName: String, lastName: String, email: String) {
  val fullName = firstName + " " + lastName
}