package core

import scala.util.matching.Regex
import play.api._
import mvc.{RequestHeader, Handler}
import eu.delving.templates.scala.RenderArgs
import models.{GrantType, PortalTheme}
import scala.collection.JavaConverters._


/**
 * A CultureHub plugin
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

abstract class CultureHubPlugin(app: Application) extends play.api.Plugin {

  val pluginKey: String

  override def enabled: Boolean = app.configuration.getString("cultureHub.plugins").map(_.split(",").map(_.trim).contains(pluginKey)).getOrElse(false)

  val routes: Map[Regex, List[String] => Handler] = Map.empty

  val onApplicationRequest: RequestContext => Unit = {
    request =>
  }

  def mainMenuEntries(theme: PortalTheme, lang: String): Seq[MainMenuEntry] = Seq.empty

  def organizationMenuEntries(context: Map[String, String], roles: Seq[String]): Seq[MainMenuEntry] = Seq.empty

  def getOrganizationNavigation(context: Map[String, String], roles: Seq[String], isMember: Boolean) = organizationMenuEntries(context, roles).
    filter(e => !e.membersOnly || (e.membersOnly && isMember && (e.roles.isEmpty || e.roles.map(_.key).intersect(roles).size > 0))).
    map(i => i.copy(items = i.items.filter(item => item.roles.isEmpty || (!item.roles.isEmpty && item.roles.map(_.key).intersect(roles).size > 0))))

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

case class RequestContext(request: RequestHeader, theme: PortalTheme, renderArgs: RenderArgs, lang: String)

