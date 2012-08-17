package plugins

import play.api.Application
import scala.util.matching.Regex
import play.api.mvc.Handler
import models.Role
import core.{MenuElement, MainMenuEntry, CultureHubPlugin}
import collection.immutable.ListMap

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class StatisticsPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "statistics"

  override val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap(
    ("GET", """^/organizations/([A-Za-z0-9-]+)/statistics/dataset$""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.statistics.Statistics.statistics(pathArgs(0))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/api/statistics$""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.statistics.Statistics.legacyStatistics(pathArgs(0))
    }
  )

  override def organizationMenuEntries(orgId: String, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "statistics",
      titleKey = "plugin.statistics.statistics",
      roles = Seq(Role.OWN),
      items = Seq(
        MenuElement(url = "/organizations/%s/statistics/dataset".format(orgId), titleKey = "plugin.statistics.dataset")
      )
    )
  )

}
