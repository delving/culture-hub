package core

import _root_.core.node.{ NodeDirectoryService, NodeRegistrationService }
import search.SearchService
import util.{ OrganizationConfigurationResourceHolder, OrganizationConfigurationHandler }
import services._
import models.{ CommonsServiceConfiguration, BaseXConfiguration, OrganizationConfiguration, HubUser }
import play.api.{ Logger, Play }
import play.api.Play.current
import storage.BaseXStorage

/**
 * Global Services used by the Hub, initialized at startup time (see ConfigurationPlugin)
 *
 * TODO find a cleaner way to initialize this object or pass it around
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object HubServices {

  val log = Logger("CultureHub")

  // ~~~ service locators for organization-bound services
  lazy val authenticationServiceLocator = new DomainServiceLocator[AuthenticationService] {
    def byDomain(implicit configuration: OrganizationConfiguration): AuthenticationService = baseServices.getResource(configuration)
  }
  lazy val registrationServiceLocator = new DomainServiceLocator[RegistrationService] {
    def byDomain(implicit configuration: OrganizationConfiguration): RegistrationService = baseServices.getResource(configuration)
  }
  lazy val userProfileServiceLocator = new DomainServiceLocator[UserProfileService] {
    def byDomain(implicit configuration: OrganizationConfiguration): UserProfileService = baseServices.getResource(configuration)
  }
  lazy val organizationServiceLocator = new DomainServiceLocator[OrganizationService] {
    def byDomain(implicit configuration: OrganizationConfiguration): OrganizationService = baseServices.getResource(configuration)
  }
  lazy val directoryServiceLocator = new DomainServiceLocator[DirectoryService] {
    def byDomain(implicit configuration: OrganizationConfiguration): DirectoryService = baseServices.getResource(configuration)
  }
  lazy val nodeRegistrationServiceLocator = new DomainServiceLocator[NodeRegistrationService] {
    def byDomain(implicit configuration: OrganizationConfiguration): NodeRegistrationService = baseServices.getResource(configuration)
  }
  lazy val nodeDirectoryServiceLocator = new DomainServiceLocator[NodeDirectoryService] {
    def byDomain(implicit configuration: OrganizationConfiguration): NodeDirectoryService = baseServices.getResource(configuration)
  }
  lazy val searchServiceLocator = new DomainServiceLocator[SearchService] {
    def byDomain(implicit configuration: OrganizationConfiguration): SearchService = CultureHubPlugin.getServices(classOf[SearchService]).head
  }
  lazy val indexingServiceLocator = new DomainServiceLocator[IndexingService] {
    def byDomain(implicit configuration: OrganizationConfiguration): IndexingService = CultureHubPlugin.getServices(classOf[IndexingService]).head
  }

  type CommonServiceType = AuthenticationService with RegistrationService with UserProfileService with OrganizationService with DirectoryService with NodeRegistrationService with NodeDirectoryService

  def basexStorages = basexStoragesHolder
  def baseServices = baseServicesHolder

  private var basexStoragesHolder: OrganizationConfigurationResourceHolder[BaseXConfiguration, BaseXStorage] = null
  private var baseServicesHolder: OrganizationConfigurationResourceHolder[(String, CommonsServiceConfiguration), CommonServiceType] = null

  def init() {

    basexStoragesHolder = new OrganizationConfigurationResourceHolder[BaseXConfiguration, BaseXStorage]("basexStorages") {

      protected def resourceConfiguration(configuration: OrganizationConfiguration): BaseXConfiguration = configuration.baseXConfiguration

      protected def onAdd(resourceConfiguration: BaseXConfiguration): Option[BaseXStorage] = {
        try {
          Some(new BaseXStorage(resourceConfiguration))
        } catch {
          case t: Throwable =>
            log.error(s"Could not initialize BaseXStorage for host ${resourceConfiguration.host}", t)
            None
        }
      }

      protected def onRemove(removed: BaseXStorage) {
        // TODO we may need to call stop() on the storage, check documentation
      }
    }

    baseServicesHolder = new OrganizationConfigurationResourceHolder[(String, CommonsServiceConfiguration), CommonServiceType]("baseServices") {

      protected def resourceConfiguration(configuration: OrganizationConfiguration): (String, CommonsServiceConfiguration) = (configuration.orgId, configuration.commonsService)

      protected def onAdd(resourceConfiguration: (String, CommonsServiceConfiguration)): Option[CommonServiceType] = {

        var services: CommonServiceType = null

        resourceConfiguration._2.commonsHost match {

          case host if (!host.isEmpty) =>
            val node = resourceConfiguration._2.nodeName
            val orgId = resourceConfiguration._1
            val apiToken = resourceConfiguration._2.apiToken
            services = new CommonsServices(host, orgId, apiToken, node)
            Some(services)

          case host if (host.isEmpty) && !Play.isProd => {
            // in development mode, load all hubUsers as basis for the remote ones
            val users = HubUser.all.flatMap {
              users =>
                users.findAll.map {
                  u =>
                    {
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
            OrganizationConfigurationHandler.getAllCurrentConfigurations.foreach {
              implicit configuration =>
                val org = MemoryOrganization(orgId = configuration.orgId, name = Map("en" -> configuration.orgId.capitalize), admins = List("bob"))
                memoryServices.organizations += (configuration.orgId -> org)

                // now ensure that bob is member everywhere
                HubUser.dao.addToOrganization("bob", configuration.orgId)
            }

            services = memoryServices
            Some(services)
          }

          case _ =>
            throw new RuntimeException("The remote services are not configured. You need to specify 'services.commons.host' and 'services.commons.apiToken")
            None
        }
      }

      protected def onRemove(removed: CommonServiceType) {
        basexStoragesHolder = null
        baseServicesHolder = null
      }

    }

    OrganizationConfigurationHandler.registerResourceHolder(basexStorages)
    OrganizationConfigurationHandler.registerResourceHolder(baseServices)
  }

}