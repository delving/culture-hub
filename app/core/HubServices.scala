package core

import services._
import models.HubUser

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

    // for local development, unless a remote service address is passed in the configuration (TODO), work with example data in memory

    // load all hubUsers as basis for the remote ones
    val users = HubUser.findAll.map(u => MemoryUser(u.userName, u.firstName, u.lastName, u.email, "secret", u.userProfile, true)).map(u => (u.userName -> u)).toMap
    val memoryServices = new MemoryServices
    users.foreach {
      u => memoryServices.users += u
    }

    // add example organization
    val delving = MemoryOrganization(orgId = "delving", name = Map("en" -> "Delving"), admins = List("bob"))
    memoryServices.organizations += ("delving" -> delving)
    
    authenticationService = memoryServices
    registrationService = memoryServices
    userProfileService = memoryServices
    organizationService = memoryServices
  }



}
