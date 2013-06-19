package plugins

import play.api.Application
import core.CultureHubPlugin

class NamedSlicesPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "namedSlices"

}

