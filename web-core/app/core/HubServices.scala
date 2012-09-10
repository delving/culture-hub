package core

import _root_.util.DomainConfigurationHandler
import scala.collection.mutable.HashMap
import services._
import models.{DomainConfiguration, HubUser}
import play.api.Play
import play.api.Play.current
import storage.BaseXStorage

/**
 * Global Services used by the Hub, initialized at startup time (see ConfigurationPlugin)
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object HubServices {

  // ~~~ service locators

  lazy val authenticationServiceLocator = new DomainServiceLocator[AuthenticationService] {
    def byDomain(implicit configuration: DomainConfiguration): AuthenticationService = baseServices(configuration)
  }
  lazy val registrationServiceLocator = new DomainServiceLocator[RegistrationService] {
    def byDomain(implicit configuration: DomainConfiguration): RegistrationService = baseServices(configuration)
  }
  lazy val userProfileServiceLocator = new DomainServiceLocator[UserProfileService] {
    def byDomain(implicit configuration: DomainConfiguration): UserProfileService = baseServices(configuration)
  }
  lazy val organizationServiceLocator = new DomainServiceLocator[OrganizationService] {
    def byDomain(implicit configuration: DomainConfiguration): OrganizationService = baseServices(configuration)
  }
  lazy val directoryServiceLocator = new DomainServiceLocator[DirectoryService] {
    def byDomain(implicit configuration: DomainConfiguration): DirectoryService = baseServices(configuration)
  }

  val basexStorage =  new HashMap[DomainConfiguration, BaseXStorage]

  var baseServices: Map[DomainConfiguration, AuthenticationService with RegistrationService with UserProfileService with OrganizationService with DirectoryService] = Map.empty

  def init() {

    baseServices = DomainConfigurationHandler.domainConfigurations.map { configuration =>

      val services = configuration.commonsService.commonsHost match {

        case host if (!host.isEmpty) =>
          val node = configuration.commonsService.nodeName
          val orgId = configuration.orgId
          val apiToken = configuration.commonsService.apiToken
          new CommonsServices(host, orgId, apiToken, node)

        case host if (host.isEmpty) && !Play.isProd => {
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

          // add example organizations
          DomainConfigurationHandler.domainConfigurations.foreach { configuration =>
            val org = MemoryOrganization(orgId = configuration.orgId, name = Map("en" -> configuration.orgId.capitalize), admins = List("bob"))
            memoryServices.organizations += (configuration.orgId -> org)

            // now ensure that bob is member everywhere
            HubUser.dao(configuration).addToOrganization("bob", configuration.orgId)
          }
          memoryServices
        }


        case _ => throw new RuntimeException("The remote services are not configured. You need to specify 'services.commons.host' and 'services.commons.apiToken")
      }

      basexStorage += (configuration -> new BaseXStorage(configuration.baseXConfiguration))

      (configuration -> services)

    }.toMap

  }


}