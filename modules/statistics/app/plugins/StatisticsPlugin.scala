package plugins

import play.api.{ Logger, Configuration, Application }
import scala.util.matching.Regex
import play.api.mvc.Handler
import models.{ OrganizationConfiguration, Role }
import core.{ RequestContext, MenuElement, MainMenuEntry, CultureHubPlugin }
import collection.immutable.ListMap
import collection.JavaConverters._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class StatisticsPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "statistics"

  /** facet key -> i18n key **/
  private var statisticsFacets: Map[OrganizationConfiguration, Map[String, String]] = Map.empty

  def getStatisticsFacets(implicit configuration: OrganizationConfiguration) = statisticsFacets.get(configuration)

  /**
   * Called at configuration building time, giving the plugin the chance to build internal configuration
   *
   */
  override def onBuildConfiguration(configurations: Map[OrganizationConfiguration, Option[Configuration]]) {
    statisticsFacets = configurations.map { config =>

      val facets = config._2.map { c =>

        c.underlying.getStringList("facets").asScala.map { facet =>
          val s: Array[String] = facet.split(':')
          if (s.length == 1) {
            (facet -> facet)
          } else if (s.length == 2) {
            (s(0) -> s(1))
          } else {
            Logger("CultureHub").warn("Invalid configuration key for statistic facets in configuration %s: %s".format(config._1.name, facet))
            (s(0) -> s(0))
          }
        }.toMap

      }.getOrElse {
        Map(
          "delving_owner_facet" -> "metadata.delving.owner",
          "delving_provider_facet" -> "metadata.delving.provider"
        )
      }

      (config._1 -> facets)

    }.toMap

  }

  override val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap(
    ("GET", """^/organizations/([A-Za-z0-9-]+)/statistics""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.statistics.Statistics.statistics(pathArgs(0))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/api/statistics$""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.statistics.Statistics.legacyStatistics(pathArgs(0))
    }
  )

  override def organizationMenuEntries(configuration: OrganizationConfiguration, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "statistics",
      titleKey = "plugin.statistics.statistics",
      roles = Seq(Role.OWN),
      mainEntry = Some(
        MenuElement(url = "/organizations/%s/statistics".format(configuration.orgId), titleKey = "plugin.statistics.statistics")
      )
    )
  )

  override def homePageSnippet: Option[(String, (RequestContext) => Unit)] = Some(
    "/homePageSnippet.html",
    { context =>
      {
        context.renderArgs += ("orgId" -> context.configuration.orgId)
      }
    }
  )

  override def roles: Seq[Role] = Seq(StatisticsPlugin.UNIT_ROLE_STATISTICS_VIEW)
}

object StatisticsPlugin {

  lazy val UNIT_ROLE_STATISTICS_VIEW = Role(
    key = "statistics-view",
    description = Map.empty,
    isUnitRole = true
  )
}