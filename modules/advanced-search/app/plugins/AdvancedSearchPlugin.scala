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

class AdvancedSearchPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "advanced-search"
  override val routes: ListMap[(String, Regex), (List[String]) => Handler] = ListMap(
    ("GET", """^/search/advanced$""") -> {
      pathArgs: List[String] => controllers.search.AdvancedSearch.advancedSearch()
    }
  )
}
