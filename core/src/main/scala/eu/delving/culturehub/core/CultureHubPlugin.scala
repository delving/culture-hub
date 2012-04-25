package eu.delving.culturehub.core

import scala.util.matching.Regex
import play.api.mvc.Handler
import play.api._


/**
 * A CultureHub plugin
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

abstract class CultureHubPlugin(app: Application) extends play.api.Plugin {

  val pluginKey: String

  override def enabled: Boolean = app.configuration.getString("cultureHub.plugins").map(_.split(",").map(_.trim).contains(pluginKey)).getOrElse(false)

  val routes: Map[Regex, List[String] => Handler] = Map.empty

}
