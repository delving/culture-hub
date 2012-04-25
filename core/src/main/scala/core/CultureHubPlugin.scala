package core

import scala.util.matching.Regex
import play.api._
import models.PortalTheme
import mvc.{RequestHeader, Handler}
import eu.delving.templates.scala.RenderArgs


/**
 * A CultureHub plugin
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

abstract class CultureHubPlugin(app: Application) extends play.api.Plugin {

  val pluginKey: String

  override def enabled: Boolean = app.configuration.getString("cultureHub.plugins").map(_.split(",").map(_.trim).contains(pluginKey)).getOrElse(false)

  val routes: Map[Regex, List[String] => Handler] = Map.empty

  val onApplicationRequest: RequestContext => Unit = { request => }

}

case class RequestContext(request: RequestHeader, theme: PortalTheme, renderArgs: RenderArgs, lang: String)

