package plugins

import _root_.util.DomainConfigurationHandler
import core.mapping.MappingService
import core.schema.SchemaRepositoryWrapper
import core.{HubModule, SchemaService, HubServices, CultureHubPlugin}
import play.api.{Play, Application}
import Play.current
import models.{Group, HubUser}
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import models.UserProfile
import play.api.libs.concurrent.Akka
import akka.actor.Props

/**
 *
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class ConfigurationPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "configuration"

  override def enabled: Boolean = true

  val schemaService: SchemaService = HubModule.inject[SchemaService](name = None)

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
      println("Using the following configurations: " + DomainConfigurationHandler.domainConfigurations.map(_.name).mkString(", "))
    }

    // ~~~ bootstrap services

    HubServices.init()
    MappingService.init()

    // ~~~ sanity check
    DomainConfigurationHandler.domainConfigurations.foreach { configuration =>
      if(!HubServices.organizationService(configuration).exists(configuration.orgId)) {
        println("Organization %s does not exist on the configured Organizations service!".format(configuration.orgId))
        System.exit(-1)
      }
    }

  }


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
        firstName = "bob",
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
