package plugins

import play.api.{Configuration, Application}
import core.CultureHubPlugin
import collection.immutable.ListMap
import scala.util.matching.Regex
import play.api.mvc.Handler
import models.DomainConfiguration

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class AdvancedSearchPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "advanced-search"

  private var advancedSearchType: Map[DomainConfiguration, Option[String]] = Map.empty

  def getAdvancedSearchType(implicit configuration: DomainConfiguration): Option[String] = advancedSearchType(configuration)


  override def onBuildConfiguration(configuration: DomainConfiguration, config: Option[Configuration]) {
    val t: Option[String] = config.flatMap(c => c.getString("type"))
    advancedSearchType = advancedSearchType + (configuration -> t)
  }

  override val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap(
    ("GET", """^/search/advanced$""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.search.AdvancedSearch.advancedSearch
    },
    ("POST", """^/search/advanced$""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.search.AdvancedSearch.submitAdvancedSearch
    }
  )
}
