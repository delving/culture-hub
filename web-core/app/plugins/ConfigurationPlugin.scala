package plugins

import core.services.AggregatingNodeSubscriptionService
import util.DomainConfigurationHandler
import core.mapping.MappingService
import core.schema.SchemaRepositoryWrapper
import core._
import play.api.{Play, Application}
import Play.current
import models.{Group, HubUser}
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import play.api.libs.concurrent.Akka
import akka.actor.{PoisonPill, ActorRef, Props}
import models.UserProfile

/**
 * This plugin runs before all others including GlobalPlugin and provides the configuration for the platform
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class ConfigurationPlugin(app: Application) extends CultureHubPlugin(app) {

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
      DomainConfigurationHandler.startup(CultureHubPlugin.hubPlugins)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        System.exit(-1)
    }

    if (!Play.isTest) {
      info("Using the following configurations: " + DomainConfigurationHandler.domainConfigurations.map(_.name).mkString(", "))
    } else {
      // now we cheat - load users before we initialize the HubServices in test mode
      onLoadTestData(Map.empty)
    }

    // ~~~ bootstrap services

    HubServices.init()

    // ~~~ sanity check
    DomainConfigurationHandler.domainConfigurations.foreach { implicit configuration =>
      if (!organizationServiceLocator.byDomain.exists(configuration.orgId)) {
        error("Organization %s does not exist on the configured Organizations service!".format(configuration.orgId))
        System.exit(-1)
      }
    }

    // ~~~ bootstrap plugin messaging, one per organization
    pluginBroadcastActors = DomainConfigurationHandler.domainConfigurations.map { implicit configuration =>
      val props = Props(new BroadcastingPluginActor)
      info("Starting Akka messaging sub-system for organization " + configuration.orgId)
      Akka.system.actorOf(props, "plugins-" + configuration.orgId)
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
