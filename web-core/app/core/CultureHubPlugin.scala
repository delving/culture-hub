package core

import core.collection.{OrganizationCollectionLookup, HarvestCollectionLookup}
import core.access.ResourceLookup
import scala.collection.immutable.ListMap
import scala.util.matching.Regex
import play.api._
import play.api.Play.current
import mvc.{RequestHeader, Handler}
import models.{Role, DomainConfiguration}
import scala.collection.JavaConverters._

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
  def mainMenuEntries(implicit configuration: DomainConfiguration, lang: String): Seq[MainMenuEntry] = Seq.empty

  /**
   * Override this to add menu entries to the organization menu
   * @param orgId the organization ID
   * @param lang the active language
   * @param roles the roles of the current user
   * @return a sequence of [[core.MainMenuEntry]] for the organization menu
   */
  def organizationMenuEntries(orgId: String, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq.empty


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

  // TODO replace the above by services

  /**
   * Override this to provide organization collections via this plugin
   *
   * @return a sequence of [[core.collection.OrganizationCollectionLookup]] instances
   */
  def organizationCollectionLookups: Seq[OrganizationCollectionLookup] = Seq.empty

  /**
   * Override this to provide harvest collections via this plugin
   *
   * @return a sequence of [[core.collection.HarvestCollectionLookup]] instances
   */
  def harvestCollectionLookups: Seq[HarvestCollectionLookup] = Seq.empty

  // ~~~ API

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
    organizationMenuEntries(orgId, lang, roles).
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

  /**
   * All available hub plugins to the application
   */
  lazy val hubPlugins: List[CultureHubPlugin] = Play.application.plugins.
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

case class MenuElement(url: String, titleKey: String, roles: Seq[Role] = Seq.empty) {
  val asJavaMap = Map(
    "url" -> url,
    "titleKey" -> titleKey
  ).asJava
}

case class RequestContext(request: RequestHeader, configuration: DomainConfiguration, renderArgs: scala.collection.mutable.Map[String, AnyRef], lang: String)