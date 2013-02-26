package plugins

import core.services.AggregatingNodeSubscriptionService
import util.OrganizationConfigurationHandler
import core.mapping.MappingService
import core.schema.SchemaRepositoryWrapper
import core._
import access.ResourceType
import play.api.{ Logger, Configuration, Play, Application }
import Play.current
import models.{ Role, Group, HubUser, UserProfile }
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import play.api.libs.concurrent.Akka
import akka.actor.{ PoisonPill, ActorRef, Props }
import scala.collection.mutable.ArrayBuffer

/**
 * This plugin runs before all others including GlobalPlugin and provides the configuration for the platform
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class ConfigurationPlugin(app: Application) extends CultureHubPlugin(app) {

  val log = Logger("CultureHub")

  val pluginKey: String = "configuration"

  override def enabled: Boolean = true

  val schemaService: SchemaService = HubModule.inject[SchemaService](name = None)
  val organizationServiceLocator = HubModule.inject[DomainServiceLocator[OrganizationService]](name = None)

  private var pluginBroadcastActors: Seq[ActorRef] = Seq.empty

  override def onStart() {

    // initialize schema repository to be available for plugins at configuration time

    Akka.system.actorOf(Props[SchemaRepositoryWrapper], name = "schemaRepository")
    schemaService.refresh()

    // ~~~ load configurations
    try {

      checkPluginSystem()

      OrganizationConfigurationHandler.configure(CultureHubPlugin.hubPlugins, isStartup = true)

    } catch {
      case t: Throwable =>
        t.printStackTrace()
        System.exit(-1)
    }

    if (!Play.isTest) {
      info("Using the following configurations: " + OrganizationConfigurationHandler.organizationConfigurations.map(_.name).mkString(", "))
    } else {
      // now we cheat - load users before we initialize the HubServices in test mode
      onLoadTestData(Map.empty)
    }

    // ~~~ bootstrap services

    HubServices.init()

    // ~~~ sanity check
    OrganizationConfigurationHandler.organizationConfigurations.foreach { implicit configuration =>
      if (!organizationServiceLocator.byDomain.exists(configuration.orgId)) {
        error("Organization %s does not exist on the configured Organizations service!".format(configuration.orgId))
        System.exit(-1)
      }
    }

    // ~~~ bootstrap plugin messaging, one per organization
    pluginBroadcastActors = OrganizationConfigurationHandler.organizationConfigurations.map { implicit configuration =>
      val props = Props(new BroadcastingPluginActor)
      info("Starting Akka messaging sub-system for organization " + configuration.orgId)
      Akka.system.actorOf(props, "plugins-" + configuration.orgId)
    }

  }

  def checkPluginSystem() {

    val plugins = CultureHubPlugin.hubPlugins

    // first we do a sanity check on the plugins
    val duplicatePluginKeys = plugins.groupBy(_.pluginKey).filter(_._2.size > 1)
    if (!duplicatePluginKeys.isEmpty) {
      log.error(
        "Found two or more plugins with the same pluginKey: " +
          duplicatePluginKeys.map(t => t._1 + ": " + t._2.map(_.getClass).mkString(", ")).mkString(", ")
      )
      throw new RuntimeException("Plugin inconsistency. No can do.")
    }

    // access control subsystem: check roles and resource handlers defined by plugins

    val duplicateRoleKeys = plugins.flatMap(plugin => plugin.roles.map(r => (r -> plugin.pluginKey))).groupBy(_._1.key).filter(_._2.size > 1)
    if (!duplicateRoleKeys.isEmpty) {
      val error = "Found two or more roles with the same key: " +
        duplicateRoleKeys.map(r => r._1 + ": " + r._2.map(pair => "Plugin " + pair._2).mkString(", ")).mkString(", ")

      log.error(error)
      throw new RuntimeException("Role definition inconsistency. No can do.\n\n" + error)
    }

    val undescribedRoles = plugins.flatMap(_.roles).filter(role => !role.isUnitRole && role.description.isEmpty)
    if (!undescribedRoles.isEmpty) {
      val error = "Found roles without a description: " + undescribedRoles.mkString(", ")
      log.error(error)
      throw new RuntimeException("Roles without description\n\n: " + error)
    }

    // make sure that if a Role defines a ResourceType, its declaring plugin also provides a ResourceLookup
    val triplets = new ArrayBuffer[(CultureHubPlugin, Role, ResourceType)]()
    plugins.foreach { plugin =>
      plugin.roles.foreach { role =>
        if (role.resourceType.isDefined) {
          val isResourceLookupProvided = plugin.resourceLookups.exists(lookup => lookup.resourceType == role.resourceType.get)
          if (!isResourceLookupProvided) {
            triplets += Tuple3(plugin, role, role.resourceType.get)
          }
        }
      }
    }
    if (!triplets.isEmpty) {
      log.error(
        """Found plugin-defined role(s) that do not provide a ResourceLookup for their ResourceType:
        |
        |Plugin\t\tRole\t\tResourceType
        |
        |%s
      """.stripMargin.format(
          triplets.map { t =>
            """%s\t\t%s\t\t%s""".format(
              t._1.pluginKey, t._2.key, t._3.resourceType
            )
          }.mkString("\n")
        )
      )
      throw new RuntimeException("Resource definition inconsistency. No can do.")
    }

  }

  override def onStop() {
    pluginBroadcastActors foreach { actor =>
      actor ! PoisonPill
    }
  }

  override def services: Seq[Any] = Seq(
    new AggregatingNodeSubscriptionService
  )

  /**
   * Executed when test data is loaded (for development and testing)
   */
  override def onLoadTestData(parameters: Map[String, Seq[String]]) {
    if (HubUser.dao("delving").count() == 0) bootstrapUser()
    if (Group.dao("delving").count() == 0) bootstrapAccessControl()

    def bootstrapUser() {
      val profile = UserProfile()
      HubUser.dao("delving").insert(new HubUser(
        _id = new ObjectId("4e5679a80364ae80333ab939"),
        userName = "bob",
        firstName = "Bob",
        lastName = "Marley",
        email = "bob@gmail.com",
        userProfile = profile
      ))
      HubUser.dao("delving").insert(new HubUser(
        _id = new ObjectId("4e5679a80364ae80333ab93a"),
        userName = "jimmy",
        firstName = "Jimmy",
        lastName = "Hendrix",
        email = "jimmy@gmail.com",
        userProfile = profile
      ))
      HubUser.dao("delving").insert(new HubUser(
        _id = new ObjectId("4e5679a80364ae80333ab93b"),
        userName = "dan",
        firstName = "Dan",
        lastName = "Brown",
        email = "dan@gmail.com",
        userProfile = profile
      ))
    }

    def bootstrapAccessControl() {
      // all users are in delving
      HubUser.dao("delving").find(MongoDBObject()).foreach(u => HubUser.dao("delving").addToOrganization(u.userName, "delving"))
    }

  }

}
