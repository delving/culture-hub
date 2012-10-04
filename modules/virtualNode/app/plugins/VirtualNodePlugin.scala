package plugins

import core.CultureHubPlugin
import play.api.Application

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class VirtualNodePlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "virtualNode"

}
