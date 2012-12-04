package plugins

import play.api._
import play.api.Play.current
import core.CultureHubPlugin

class FilePlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "file"
}
