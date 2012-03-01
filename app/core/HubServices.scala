package core

import services._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object HubServices {

  // ~~~ service references
  // TODO decide on a dependency injection mechanism

  var authenticationService: AuthenticationService = null
  var registrationService: RegistrationService = null
  var userProfileService: UserProfileService = null
  var organizationService: OrganizationService = null


  def init() {
    authenticationService = new MongoAuthenticationService
    registrationService = new MongoRegistrationService
    userProfileService = new MongoUserProfileService
    organizationService = new MongoOrganizationService
  }



}
