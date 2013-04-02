package plugins

import play.api.Application
import core.CultureHubPlugin

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class EADPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "ead"

}
