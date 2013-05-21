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

  private var statisticsConfigurations = Map.empty[OrganizationConfiguration, StatisticsPluginConfiguration]

  def getStatisticsConfiguration(implicit configuration: OrganizationConfiguration) = statisticsConfigurations.get(configuration)

  override def onBuildConfiguration(configurations: Map[OrganizationConfiguration, Option[Configuration]]) {
    statisticsConfigurations = configurations.map { config =>

      val defaults = Map(
        "delving_owner_facet" -> "md.delving.owner",
        "delving_provider_facet" -> "md.delving.provider"
      )

      val facets = config._2.map { c =>

        if (c.underlying.hasPath("facets")) {
          c.underlying.getStringList("facets").asScala.map { facet =>
            val s: Array[String] = facet.split(':')
            if (s.length == 1) {
              (facet -> facet)
            } else if (s.length == 2) {
              (s(0) -> s(1))
            } else {
              Logger("CultureHub").warn("Invalid configuration key for statistic facets in configuration %s: %s".format(config._1.orgId, facet))
              (s(0) -> s(0))
            }
          }.toMap
        } else {
          defaults
        }

      }.getOrElse {
        defaults
      }

      val public = config._2.map { c =>
        c.getBoolean("public").getOrElse(false)
      }.getOrElse {
        false
      }

      (config._1 -> StatisticsPluginConfiguration(facets = facets, public = public))

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
      titleKey = "stats.Statistics",
      roles = Seq(Role.OWN),
      mainEntry = Some(
        MenuElement(url = "/organizations/%s/statistics".format(configuration.orgId), titleKey = "stats.Statistics")
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

case class StatisticsPluginConfiguration(
  /** facet key -> i18n key **/
  facets: Map[String, String] = Map.empty,
  public: Boolean = false)

object StatisticsPlugin {

  lazy val UNIT_ROLE_STATISTICS_VIEW = Role(
    key = "statistics-view",
    description = Map.empty,
    isUnitRole = true
  )
}