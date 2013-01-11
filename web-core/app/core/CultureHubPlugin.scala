package core

import _root_.util.DomainConfigurationHandler
import core.access.ResourceLookup
import scala.collection.immutable.ListMap
import scala.util.matching.Regex
import play.api._
import libs.concurrent.Akka
import play.api.Play.current
import mvc.{RequestHeader, Handler}
import models.{Role, DomainConfiguration}
import scala.collection.JavaConverters._
import akka.actor.{ActorRef, Props, Actor}

/**
 * The CultureHub plugin contract, allowing to influence the appearance and functionality of the Hub.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

abstract class CultureHubPlugin(app: Application) extends play.api.Plugin {

  // ~~~ Implementation

  /**
   * The key of this plugin, must be unique across all deployed plugins.
   * Should not contains spaces or weird characters, keep it simple, e.g. "semantic-enrichment"
   */
  val pluginKey: String

  /**
   * An ordered map that mimics the routing and uses regular expressions in order to extract the parameters.
   * This will probably disappear when Play will have built-in support for multi-project routes
   *
   * Example of a route:
   *
   * {{{
   *
   *   ("GET", """^/user/([A-Za-z0-9-]+)/profile""".r) -> {
   *     pathArgs: List[String] => controllers.user.Profile.view(pathArgs(0))
   *   }
   *
   * }}}
   *
   */
  val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap.empty

  /**
   * Called at configuration building time, giving the plugin the chance to build internal configuration
   *
   */
  def onBuildConfiguration(configurations: Map[DomainConfiguration, Option[Configuration]]) {}

  /**
   * Helper method for configuration building
   * @param field the configuration field path that is missing
   * @return
   */
  def missingConfigurationField(field: String, domainConfigurationName: String) = {
    new RuntimeException(
      "Missing field %s for configuration of plugin %s for DomainConfiguration %s".format(
        field, pluginKey, domainConfigurationName
      )
    )
  }

  /**
   * Executed when test data is loaded (for development and testing)
   */
  def onLoadTestData(parameters: Map[String, Seq[String]]) {}

  /**
   * Override this to add menu entries to the main menu
   *
   * @param configuration the [[models.DomainConfiguration]]
   * @param lang the active language
   * @return a sequence of [[core.MainMenuEntry]] for the main menu
   */
  def mainMenuEntries(configuration: DomainConfiguration, lang: String): Seq[MainMenuEntry] = Seq.empty

  /**
   * Override this to add menu entries to the organization menu
   * @param configuration the [[models.DomainConfiguration]]
   * @param lang the active language
   * @param roles the roles of the current user
   * @return a sequence of [[core.MainMenuEntry]] for the organization menu
   */
  def organizationMenuEntries(configuration: DomainConfiguration, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq.empty

  /**
   * Override this to include a snippet in the homePage.
   *
   * Caution: this is an experimental API feature and might disappear at any time!
   */
  def homePageSnippet: Option[(String, RequestContext => Unit)] = None

  /**
   * Override this to include an additional snippet in the full view page
   *
   * Caution: this is an experimental API feature and might disappear at any time!
   */
  def fullViewSnippet: Option[(String, (RequestContext, HubId) => Unit)] = None

  /**
   * Override this to include an additional snippet in the search result page
   *
   * Caution: this is an experimental API feature and might disappear at any time!
   */
  def searchResultSnippet: Option[(String, RequestContext => Unit)] = None

  /**
   * Override this to provide custom roles to the platform, that can be used in Groups
   * @return a sequence of [[models.Role]] instances
   */
  def roles: Seq[Role] = Seq.empty

  /**
   * Override this to provide the necessary lookup for a [[core.access.Resource]] depicted by a [[models.Role]]
   * @return
   **/
  def resourceLookups: Seq[ResourceLookup] = Seq.empty

  /**
   * Service instances this plugin provides
   */
  def services: Seq[Any] = Seq.empty

  /**
   * Handler for plugin messaging, based on Akka actors.
   * Override this method to handle particular messages.
   */
  def receive: Actor.Receive = { case _ @ message => }


  // ~~~ API

  private val log = Logger("CultureHub")

  protected def info(message: String) { log.info("[plugin %s] %s".format(pluginKey, message)) }
  protected def debug(message: String) { log.debug("[plugin %s] %s".format(pluginKey, message)) }
  protected def error(message: String) { log.error("[plugin %s] %s".format(pluginKey, message)) }
  protected def error(message: String, t: Throwable) { "[plugin %s] %s".format(pluginKey, message, t) }

  /** whether this plugin is enabled for the current domain **/
  def isEnabled(configuration: DomainConfiguration): Boolean = configuration.plugins.exists(_ == pluginKey) || pluginKey == "configuration"

  /**
   * Retrieves the navigation for the organization section of the Hub
   * @param roles the roles of the current user
   * @param isMember whether the user is a member of the organization
   * @param configuration the [[models.DomainConfiguration]]
   * @return a sequence of MenuEntries
   */
  def getOrganizationNavigation(orgId: String, lang: String, roles: Seq[String], isMember: Boolean)(implicit configuration: DomainConfiguration) = if(isEnabled(configuration)) {
    organizationMenuEntries(configuration, lang, roles).
      filter(e => !e.membersOnly || (e.membersOnly && isMember && (e.roles.isEmpty || e.roles.map(_.key).intersect(roles).size > 0))).
      map(i => i.copy(items = i.items.filter(item => item.roles.isEmpty || (!item.roles.isEmpty && item.roles.map(_.key).intersect(roles).size > 0))))
  } else {
    Seq.empty
  }

  /**
   * Gets all service implementations of a certain type
   */
  def getServices[T <: Any](serviceClass: Class[T]): Seq[T] = {
    services.filter(s => serviceClass.isAssignableFrom(s.getClass)).map(_.asInstanceOf[T])
  }

  /**
   * Gets a single service of a certain type, for services that should be provided only once accross the platform
   */
  def getSingleService[T <: Any](serviceClass: Class[T]): Option[T] = {
    services.find(s => serviceClass.isAssignableFrom(s.getClass)).map(_.asInstanceOf[T])
  }


  // ~~~ Play Plugin lifecycle integration

  /** finds out whether this plugin is enabled at all, for the whole hub **/
  override def enabled: Boolean = {
    val config = app.configuration.getConfig("configurations").get
    val allDomainConfigurations: Seq[String] = config.keys.filterNot(_.indexOf(".") < 0).map(_.split("\\.").head).toList.distinct
    val plugins: Seq[String] = allDomainConfigurations.flatMap {
      key => {
        val configuration = config.getConfig(key).get
        configuration.underlying.getStringList("plugins").asScala.toSeq
      }
    }
    plugins.distinct.contains(pluginKey)
  }

  override def hashCode(): Int = pluginKey.hashCode

  override def equals(plugin: Any): Boolean = plugin.isInstanceOf[CultureHubPlugin] && plugin.asInstanceOf[CultureHubPlugin].pluginKey == pluginKey



}

object CultureHubPlugin {

  lazy val broadcastingPluginActorReferences: Map[DomainConfiguration, ActorRef] = {
    DomainConfigurationHandler.domainConfigurations.map { implicit configuration =>
      (configuration -> Akka.system.actorFor("akka://application/user/plugins-" + configuration.orgId))
    }.toMap
  }

  /**
   * All available hub plugins to the application
   */
  def hubPlugins: List[CultureHubPlugin] = Play.application.plugins.
    filter(_.isInstanceOf[CultureHubPlugin]).
    map(_.asInstanceOf[CultureHubPlugin]).
    toList

  /**
   * Retrieves all enabled plugins for the current domain
   * @param configuration the [[models.DomainConfiguration]] being accessed
   * @return the set of active plugins
   */
  def getEnabledPlugins(implicit configuration: DomainConfiguration): Seq[CultureHubPlugin] = Play.application.plugins
      .filter(_.isInstanceOf[CultureHubPlugin])
      .map(_.asInstanceOf[CultureHubPlugin])
      .filter(_.isEnabled(configuration))
      .toList
      .distinct

  /**
   * Gets all service implementations of a certain type provided by all plugins
   */
  def getServices[T <: Any](serviceClass: Class[T])(implicit configuration: DomainConfiguration): Seq[T] = {
    getEnabledPlugins.flatMap { p =>
      p.getServices(serviceClass)
    }
  }

  /**
   * Gets the CultureHubPlugin of a certain type
   * @param pluginClass the class of the plugin
   * @param configuration the [[models.DomainConfiguration]] being accessed
   * @tparam T the type of the plugin
   * @return an instance of T if there is any
   */
  def getPlugin[T <: CultureHubPlugin](pluginClass: Class[T])(implicit configuration: DomainConfiguration): Option[T] = {
    getEnabledPlugins.find(p => pluginClass.isAssignableFrom(p.getClass)).map(_.asInstanceOf[T])
  }

  /**
   * Asynchronously broadcasts a message to all active plugins of a configuration
   * @param message the message to send
   * @param configuration the [[models.DomainConfiguration]] being accessed
   */
  def broadcastMessage(message: Any)(implicit configuration: DomainConfiguration) {
    broadcastingPluginActorReferences.get(configuration).map { ref =>
      ref ! message
    }.getOrElse {
      Logger("CultureHub").warn("Could not broadcast message %s to plugins of organization %s: no actor found".format(message, configuration.orgId))
    }
  }


}

case class MainMenuEntry(key: String, titleKey: String, roles: Seq[Role] = Seq.empty, items: Seq[MenuElement] = Seq.empty, mainEntry: Option[MenuElement] = None, membersOnly: Boolean = true) {

  def asJavaMap = Map(
    "key" -> key,
    "titleKey" -> titleKey,
    "roles" -> roles.map(_.key).asJava,
    "items" -> items.map(_.asJavaMap).toList.asJava,
    "mainEntry" -> (if (mainEntry.isDefined) mainEntry.get.asJavaMap else Map.empty.asJava)
  ).asJava
}

case class MenuElement(url: String, titleKey: String, roles: Seq[Role] = Seq.empty, isDivider: Boolean = false) {
  val asJavaMap = Map(
    "url" -> url,
    "titleKey" -> titleKey,
    "isDivider" -> isDivider
  ).asJava
}

case class RequestContext(request: RequestHeader, configuration: DomainConfiguration, renderArgs: scala.collection.mutable.Map[String, AnyRef], lang: String)

class BroadcastingPluginActor(implicit configuration: DomainConfiguration) extends Actor {

  var routees: Seq[ActorRef] = Seq.empty


  override def preStart() {
    routees = CultureHubPlugin.getEnabledPlugins.map { plugin =>
      context.actorOf(Props(new PluginActor(plugin)))
    }
  }

  protected def receive: BroadcastingPluginActor#Receive = {
    case message@_ =>
      routees.foreach { r => r ! message }
  }
}

class PluginActor(plugin: CultureHubPlugin) extends Actor {
  protected def receive: PluginActor#Receive = plugin.receive
}