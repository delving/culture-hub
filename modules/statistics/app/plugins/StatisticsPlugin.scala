package plugins

import play.api.{Logger, Configuration, Application}
import scala.util.matching.Regex
import play.api.mvc.Handler
import models.{DomainConfiguration, Role}
import core.{MenuElement, MainMenuEntry, CultureHubPlugin}
import collection.immutable.ListMap
import collection.JavaConverters._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class StatisticsPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "statistics"

  /** facet key -> i18n key **/
  private var statisticsFacets: Map[String, String] = null

  def getStatisticsFacets = statisticsFacets

  /**
   * Called at configuration building time, giving the plugin the chance to build internal configuration
   *
   * @param configuration the DomainConfiguration
   * @param config the Play Configuration object
   */
  override def onBuildConfiguration(configuration: DomainConfiguration, config: Option[Configuration]) {
    statisticsFacets = config.map {
      c =>
        c.underlying.getStringList("facets").asScala.map {
          facet => {
            val s = facet.split(":")
            if (s.length == 0) {
              (facet -> facet)
            } else if (s.length == 1) {
              (s.head -> s.reverse.head)
            } else {
              Logger("CultureHub").warn("Invalid configuration key for statistic facets in configuration %s: %s".format(configuration.name, facet))
              (s.head -> s.head)
            }
          }
        }.toMap
    }.getOrElse {
      Map(
        "delving_owner" -> "metadata.delving.owner",
        "delving_provider" -> "metadata.delving.provider"
      )
    }
  }

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
