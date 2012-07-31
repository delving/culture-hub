package core

import _root_.util.DomainConfigurationHandler
import scala.collection.mutable.HashMap
import services._
import models.{DomainConfiguration, HubUser}
import play.api.Play
import play.api.Play.current
import storage.BaseXStorage

/**
 * Global Services used by the Hub, initialized at startup time (see Global)
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object HubServices {

  // ~~~ service references

  val authenticationService = new HashMap[DomainConfiguration, AuthenticationService]
  val registrationService = new HashMap[DomainConfiguration, RegistrationService]
  val userProfileService = new HashMap[DomainConfiguration, UserProfileService]
  val organizationService = new HashMap[DomainConfiguration, OrganizationService]
  val directoryService = new HashMap[DomainConfiguration, DirectoryService]

  val basexStorage =  new HashMap[DomainConfiguration, BaseXStorage]

  def init() {

    DomainConfigurationHandler.domainConfigurations.foreach { configuration =>

      val services = configuration.commonsService.commonsHost match {

        case host if (!host.isEmpty) =>
          val node = configuration.commonsService.nodeName
          val orgId = configuration.orgId
          val apiToken = configuration.commonsService.apiToken
          new CommonsServices(host, orgId, apiToken, node)

        case host if (host.isEmpty) && !Play.isProd =>
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

      Seq(authenticationService, registrationService, userProfileService, organizationService, directoryService).foreach(_ += (configuration -> services))
      basexStorage += (configuration -> new BaseXStorage(configuration.baseXConfiguration))
    }
  }
}
