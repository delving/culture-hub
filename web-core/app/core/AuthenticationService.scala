package core

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait AuthenticationService {

  def connect(userName: String, password: String): Boolean

}