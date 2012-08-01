package core

import collection.HarvestCollectionLookup
import scala.collection.immutable.ListMap
import scala.util.matching.Regex
import play.api._
import play.api.Play.current
import mvc.{RequestHeader, Handler}
import models.{GrantType, DomainConfiguration}
import scala.collection.JavaConverters._

/**
 * A CultureHub plugin
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

abstract class CultureHubPlugin(app: Application) extends play.api.Plugin {

  val pluginKey: String

  /** whether this plugin is enabled for the whole hub **/
  override def enabled: Boolean = {
    val config = app.configuration.getConfig("configurations").get
    val allDomainConfigurations = config.keys.filterNot(_.indexOf(".") < 0).map(_.split("\\.").head).toList.distinct
    val plugins: Seq[String] = allDomainConfigurations.flatMap {
      key => {
        val configuration = config.getConfig(key).get
        configuration.underlying.getStringList("plugins").asScala.toSeq
      }
    }
    plugins.distinct.contains(pluginKey)
  }

  /** whether this plugin is enabled for the current domain **/
  def isEnabled(configuration: DomainConfiguration): Boolean = configuration.plugins.exists(_ == pluginKey)

  val routes: ListMap[(String, Regex), List[String] => Handler] = ListMap.empty

  def onApplicationStart() { }

  def mainMenuEntries(implicit configuration: DomainConfiguration, lang: String): Seq[MainMenuEntry] = Seq.empty

  def organizationMenuEntries(context: Map[String, String], roles: Seq[String]): Seq[MainMenuEntry] = Seq.empty

  def getOrganizationNavigation(context: Map[String, String], roles: Seq[String], isMember: Boolean)(implicit configuration: DomainConfiguration) = if(isEnabled(configuration)) {
    organizationMenuEntries(context, roles).
      filter(e => !e.membersOnly || (e.membersOnly && isMember && (e.roles.isEmpty || e.roles.map(_.key).intersect(roles).size > 0))).
      map(i => i.copy(items = i.items.filter(item => item.roles.isEmpty || (!item.roles.isEmpty && item.roles.map(_.key).intersect(roles).size > 0))))
  } else {
    Seq.empty
  }

  def getHarvestCollectionLookups: Seq[HarvestCollectionLookup] = List.empty

}

object CultureHubPlugin {

  def getEnabledPlugins(implicit configuration: DomainConfiguration): List[CultureHubPlugin] = Play.application.plugins
      .filter(_.isInstanceOf[CultureHubPlugin])
      .map(_.asInstanceOf[CultureHubPlugin])
      .filter(_.isEnabled(configuration))
      .toList

}

case class MainMenuEntry(key: String, titleKey: String, roles: Seq[GrantType] = Seq.empty, items: Seq[MenuElement] = Seq.empty, mainEntry: Option[MenuElement] = None, membersOnly: Boolean = true) {

  def asJavaMap = Map(
    "key" -> key,
    "titleKey" -> titleKey,
    "roles" -> roles.map(_.key).asJava,
    "items" -> items.map(_.asJavaMap).toList.asJava,
    "mainEntry" -> (if (mainEntry.isDefined) mainEntry.get.asJavaMap else Map.empty.asJava)
  ).asJava
}

case class MenuElement(url: String, titleKey: String, roles: Seq[GrantType] = Seq.empty) {
  val asJavaMap = Map(
    "url" -> url,
    "titleKey" -> titleKey
  ).asJava
}

case class RequestContext(request: RequestHeader, configuration: DomainConfiguration, renderArgs: scala.collection.mutable.Map[String, AnyRef], lang: String)

