package core.services

import core.AuthenticationService
import models.User

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class MongoAuthenticationService extends AuthenticationService {

  def connect(userName: String, password: String) = User.connect(userName, password)
}
