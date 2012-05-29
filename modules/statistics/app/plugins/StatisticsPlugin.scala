package plugins

import play.api.Application
import scala.util.matching.Regex
import play.api.mvc.Handler
import models.GrantType
import core.{MenuElement, MainMenuEntry, CultureHubPlugin}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class StatisticsPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "statistics"

  override val routes: Map[Regex, (List[String]) => Handler] = Map(
    """^/organizations/([A-Za-z0-9-]+)/statistics/dataset$""".r -> {
      pathArgs: List[String] => controllers.statistics.DataSetStatistics.statistics(pathArgs(0))
    })

  override def organizationMenuEntries(context: Map[String, String], roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "statistics",
      titleKey = "plugin.statistics.statistics",
      roles = Seq(GrantType.OWN),
      items = Seq(
        MenuElement(url = "/organizations/%s/statistics/dataset".format(context("orgId")), titleKey = "plugin.statistics.dataset")
      )
    )
  )

}
