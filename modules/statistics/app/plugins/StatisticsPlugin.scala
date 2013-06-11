package plugins

import play.api.{ Logger, Configuration, Application }
import models.{ OrganizationConfiguration, Role }
import core._
import scala.collection.JavaConverters._
import core.MainMenuEntry
import core.RequestContext
import core.MenuElement

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
        "delving_owner_facet" -> "metadata.delving.owner",
        "delving_provider_facet" -> "metadata.delving.provider"
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

  override def organizationMenuEntries(configuration: OrganizationConfiguration, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "statistics",
      titleKey = "stats.Statistics",
      roles = Seq(Role.OWN),
      mainEntry = Some(
        MenuElement(url = "/statistics".format(configuration.orgId), titleKey = "stats.Statistics")
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