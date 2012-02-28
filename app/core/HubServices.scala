package core

import services._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object HubServices {


  // service references
  // TODO decide on a dependency injection mechanism

  var authenticationService: AuthenticationService = null
  var registrationService: RegistrationService = null



  def init() {
    authenticationService = new MongoAuthenticationService
    registrationService = new MongoRegistrationService
  }



}
