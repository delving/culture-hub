package core

import services._
import models.{DomainConfiguration, HubUser}
import play.api.Play
import play.api.Play.current
import storage.BaseXStorage

/**
 * Global Services used by the Hub, initialized at startup time (see Global)
 *
 * TODO decide on a dependency injection mechanism
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object HubServices {

  // ~~~ service references

  var authenticationService: AuthenticationService = null
  var registrationService: RegistrationService = null
  var userProfileService: UserProfileService = null
  var organizationService: OrganizationService = null
  var directoryService: DirectoryService = null

  var basexStorage: BaseXStorage = null

  def init() {

    val services = Play.configuration.getString("cultureCommons.host") match {
      case Some(host) =>
        val node = Play.configuration.getString("cultureHub.nodeName").getOrElse(throw new RuntimeException("No nodeName provided"))
        val orgId = Play.configuration.getString("cultureHub.orgId").getOrElse(throw new RuntimeException("No orgId provided"))
        val apiToken = Play.configuration.getString("cultureCommons.apiToken").getOrElse(throw new RuntimeException("No api token provided"))
        new CommonsServices(host, orgId, apiToken, node)
      case None if !Play.isProd =>
        // in development mode, load all hubUsers as basis for the remote ones
        val users = HubUser.all.flatMap { users =>
          users.findAll.map {
            u => {
              MemoryUser(
                u.userName,
                u.firstName,
                u.lastName,
                u.email,
                "secret",
                u.userProfile,
                true
              )
            }
          }
        }.map(u => (u.userName -> u)).toMap

        val memoryServices = new MemoryServices
        users.foreach {
          u => memoryServices.users += u
        }

        // add example organization
        val delving = MemoryOrganization(orgId = "delving", name = Map("en" -> "Delving"), admins = List("bob"))
        memoryServices.organizations += ("delving" -> delving)

        memoryServices
      case _ => throw new RuntimeException("The remote services are not configured. You need to specify 'cultureCommons.host' and 'cultureCommons.apiToken")
    }

    authenticationService = services
    registrationService = services
    userProfileService = services
    organizationService = services
    directoryService = services

    basexStorage = new BaseXStorage(
      Play.configuration.getString("basex.host").getOrElse("localhost"),
      Play.configuration.getInt("basex.port").getOrElse(1984),
      Play.configuration.getInt("basex.eport").getOrElse(1985),
      Play.configuration.getString("basex.user").getOrElse("admin"),
      Play.configuration.getString("basex.password").getOrElse("admin")
    )

  }
}
