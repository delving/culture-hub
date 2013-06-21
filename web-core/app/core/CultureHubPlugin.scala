package core

import util.{ OrganizationConfigurationResourceHolder, OrganizationConfigurationHandler }
import access.{ ResourceType, ResourceLookup }
import scala.collection.immutable.ListMap
import scala.util.matching.Regex
import play.api._
import libs.concurrent.Akka
import play.api.Play.current
import mvc.{ RequestHeader, Handler }
import models.{ Role, OrganizationConfiguration }
import scala.collection.JavaConverters._
import akka.actor.{ ActorContext, ActorRef, Props, Actor }
import play.core.Router.Routes

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
  @deprecated(message = "use the router method", since = "13.06")
  val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap.empty

  /**
   * Optional routes this plugin provides.
   *
   * For example, with default Play routes:
   *
   * {{{
   *   val router = Some(myPlugin.Routes)
   * }}}
   *
   */
  val router: Option[Routes] = None

  /**
   * Called at configuration building time, giving the plugin the chance to build internal configuration
   *
   */
  def onBuildConfiguration(configurations: Map[OrganizationConfiguration, Option[Configuration]]) {}

  /**
   * Called at actor initialization time. Plugins that make use of the ActorSystem should initialize their actors here
   * @param context the [[ ActorContext ]] for this plugin
   */
  def onActorInitialization(context: ActorContext) {}

  /**
   * Helper method for configuration building
   * @param field the configuration field path that is missing
   * @return
   */
  def missingConfigurationField(field: String, organizationConfigurationName: String) = {
    new RuntimeException(
      "Missing field %s for configuration of plugin %s for OrganizationConfiguration %s".format(
        field, pluginKey, organizationConfigurationName
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
   * @param configuration the [[models.OrganizationConfiguration]]
   * @param lang the active language
   * @return a sequence of [[core.MainMenuEntry]] for the main menu
   */
  def mainMenuEntries(configuration: OrganizationConfiguration, lang: String): Seq[MainMenuEntry] = Seq.empty

  /**
   * Override this to add menu entries to the organization menu
   * @param configuration the [[models.OrganizationConfiguration]]
   * @param lang the active language
   * @param roles the roles of the current user
   * @return a sequence of [[core.MainMenuEntry]] for the organization menu
   */
  def organizationMenuEntries(configuration: OrganizationConfiguration, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq.empty

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
   */
  def resourceLookups: Seq[ResourceLookup] = Seq.empty

  /**
   * Service instances this plugin provides
   */
  def services: Seq[Any] = Seq.empty

  /**
   * Handler for plugin messaging, based on Akka actors.
   * Override this method to handle particular messages.
   */
  def receive: Actor.Receive = { case _@ message => }

  // ~~~ API

  private val log = Logger("CultureHub")

  protected def info(message: String) { log.info("[plugin %s] %s".format(pluginKey, message)) }
  protected def debug(message: String) { log.debug("[plugin %s] %s".format(pluginKey, message)) }
  protected def error(message: String) { log.error("[plugin %s] %s".format(pluginKey, message)) }
  protected def error(message: String, t: Throwable) { "[plugin %s] %s".format(pluginKey, message, t) }

  /** whether this plugin is enabled for the current domain **/
  def isEnabled(configuration: OrganizationConfiguration): Boolean = configuration.plugins.exists(_ == pluginKey) || pluginKey == "configuration"

  /**
   * Retrieves the navigation for the organization section of the Hub
   * @param roles the roles of the current user
   * @param isMember whether the user is a member of the organization
   * @param configuration the [[models.OrganizationConfiguration]]
   * @return a sequence of MenuEntries
   */
  def getOrganizationNavigation(orgId: String, lang: String, roles: Seq[String], isMember: Boolean)(implicit configuration: OrganizationConfiguration) = if (isEnabled(configuration)) {
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
    app.configuration.getConfig("configurations").map { config =>
      val allOrganizationConfigurations: Seq[String] = config.keys.filterNot(_.indexOf(".") < 0).map(_.split("\\.").head).toList.distinct
      val plugins: Seq[String] = allOrganizationConfigurations.flatMap {
        key =>
          {
            val configuration = config.getConfig(key).get
            configuration.underlying.getStringList("plugins").asScala.toSeq
          }
      }
      plugins.distinct.contains(pluginKey)
    }.getOrElse {
      if (app.configuration.underlying.hasPath("cultureHub.plugins")) {
        app.configuration.underlying.getStringList("cultureHub.plugins").asScala.contains(pluginKey)
      } else {
        log.error("Fatal error: could not read plugin configurations, config is:\n" + app.configuration.underlying.origin())
        System.exit(-1)
        false
      }
    }
  }

  override def hashCode(): Int = pluginKey.hashCode

  override def equals(plugin: Any): Boolean = plugin.isInstanceOf[CultureHubPlugin] && plugin.asInstanceOf[CultureHubPlugin].pluginKey == pluginKey

}

object CultureHubPlugin {

  val broadcastingPluginActorReferences = new OrganizationConfigurationResourceHolder[OrganizationConfiguration, ActorRef]("broadcastingPluginActorReferences") {

    protected def resourceConfiguration(configuration: OrganizationConfiguration): OrganizationConfiguration = configuration

    protected def onAdd(resourceConfiguration: OrganizationConfiguration): Option[ActorRef] = Some(Akka.system.actorFor("akka://application/user/plugins-" + resourceConfiguration.orgId))

    protected def onRemove(removed: ActorRef) { Akka.system.stop(removed) }
  }

  OrganizationConfigurationHandler.registerResourceHolder(broadcastingPluginActorReferences)

  /**
   * All available hub plugins to the application
   */
  def hubPlugins: List[CultureHubPlugin] = Play.application.plugins.
    filter(_.isInstanceOf[CultureHubPlugin]).
    map(_.asInstanceOf[CultureHubPlugin]).
    toList

  /**
   * Retrieves all enabled plugins for the current domain
   * @param configuration the [[models.OrganizationConfiguration]] being accessed
   * @return the set of active plugins
   */
  def getEnabledPlugins(implicit configuration: OrganizationConfiguration): Seq[CultureHubPlugin] = Play.application.plugins
    .filter(_.isInstanceOf[CultureHubPlugin])
    .map(_.asInstanceOf[CultureHubPlugin])
    .filter(_.isEnabled(configuration))
    .toList
    .distinct

  /**
   * Gets all service implementations of a certain type provided by all plugins
   */
  def getServices[T <: Any](serviceClass: Class[T])(implicit configuration: OrganizationConfiguration): Seq[T] = {
    getEnabledPlugins.flatMap { p =>
      p.getServices(serviceClass)
    }
  }

  /**
   * Gets the CultureHubPlugin of a certain type
   * @param pluginClass the class of the plugin
   * @param configuration the [[models.OrganizationConfiguration]] being accessed
   * @tparam T the type of the plugin
   * @return an instance of T if there is any
   */
  def getPlugin[T <: CultureHubPlugin](pluginClass: Class[T])(implicit configuration: OrganizationConfiguration): Option[T] = {
    getEnabledPlugins.find(p => pluginClass.isAssignableFrom(p.getClass)).map(_.asInstanceOf[T])
  }

  /**
   * Asynchronously broadcasts a message to all active plugins of a configuration
   * @param message the message to send
   * @param configuration the [[models.OrganizationConfiguration]] being accessed
   */
  def broadcastMessage(message: Any)(implicit configuration: OrganizationConfiguration) {
    broadcastingPluginActorReferences.getResource(configuration) ! message
  }

  /**
   * Find the ResourceLookup for a given ResourceType
   * @param resourceType the [[ ResourceType ]] for this lookup
   * @param configuration the [[models.OrganizationConfiguration]] being accessed
   * @return an optional [[ ResourceLookup]]
   */
  def getResourceLookup(resourceType: ResourceType)(implicit configuration: OrganizationConfiguration): Option[ResourceLookup] = {
    CultureHubPlugin.
      getEnabledPlugins.
      flatMap(plugin => plugin.resourceLookups).
      find(lookup => lookup.resourceType == resourceType)
  }

  /**
   * Whether the quota for this resource has been reached or exceeded
   * @param resourceType the [[ ResourceType ]] for which to check the quota
   * @param configuration the [[models.OrganizationConfiguration]] being accessed
   * @return whether the quota has been reached or exceeded
   */
  def isQuotaExceeded(resourceType: ResourceType)(implicit configuration: OrganizationConfiguration): Boolean = {
    configuration.quotas.get(resourceType.resourceType).map { limit =>
      limit > -1 && (
        getResourceLookup(resourceType).map { lookup =>
          lookup.totalResourceCount >= limit
        }.getOrElse(false)
      )
    }.getOrElse(false)
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

case class RequestContext(request: RequestHeader, configuration: OrganizationConfiguration, renderArgs: scala.collection.mutable.Map[String, AnyRef], lang: String)

// ~~~ Plugin Messaging

class BroadcastingPluginActor(implicit configuration: OrganizationConfiguration) extends Actor {

  var routees: Seq[ActorRef] = Seq.empty

  override def preStart() {
    routees = CultureHubPlugin.getEnabledPlugins.map { plugin =>
      context.actorOf(Props(new PluginActor(plugin)))
    }
  }

  def receive: BroadcastingPluginActor#Receive = {
    case message @ _ =>
      routees.foreach { r => r ! message }
  }
}

class PluginActor(plugin: CultureHubPlugin) extends Actor {
  def receive: PluginActor#Receive = plugin.receive
}

// ~~~ Plugin Actor

case class Initialize(onInit: ActorContext => Unit)

class PluginRootActor(plugin: CultureHubPlugin) extends Actor {

  private val log = Logger("CultureHub")

  override def preStart() {
    plugin.onActorInitialization(context)
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    log.error(s"Root actor for plugin ${plugin.pluginKey} is restarting because: ${reason.getMessage}", reason)
    super.preRestart(reason, message)
  }

  def receive: PluginRootActor#Receive = {
    case _ => // don't do anything. we simply supervise.
  }
}

object PluginRootActor {

  def initialize(actor: ActorRef, onInit: ActorContext => Unit) {
    actor ! Initialize(onInit)
  }

}