package plugins

import play.api.Application
import core.CultureHubPlugin
import collection.immutable.ListMap
import scala.util.matching.Regex
import play.api.mvc.Handler

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class ItinPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "itin"

  /*
   *  GET         /services/api/itin                                               controllers.custom.ItinEndPoint.search
   *  POST        /services/api/itin                                               controllers.custom.ItinEndPoint.store
   */
  override val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap(
    (("GET", """/services/api/itin""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.itin.ItinEndPoint.search
    }),
    (("POST", """/services/api/itin""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.itin.ItinEndPoint.store
    })
  )
}
