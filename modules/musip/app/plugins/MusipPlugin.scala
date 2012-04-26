package plugins

import core.CultureHubPlugin
import play.api.mvc.Handler
import scala.util.matching.Regex
import play.api.Application

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class MusipPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "musip"

  override val routes: Map[Regex, List[String] => Handler] = Map(
    """^/([A-Za-z0-9-]+)/collection/([A-Za-z0-9-]+)$""".r -> {
      pathArgs: List[String] => controllers.musip.Show.collection(pathArgs(0), pathArgs(1))
    },
    """^/([A-Za-z0-9-]+)/museum/([A-Za-z0-9-]+)$""".r -> {
      pathArgs: List[String] => controllers.musip.Show.museum(pathArgs(0), pathArgs(1))
    },
    """^/organizations/([A-Za-z0-9-]+)/admin/musip/synchronize$""".r -> {
      pathArgs: List[String] => controllers.musip.Admin.synchronize(pathArgs(0))
    }


  )
}
