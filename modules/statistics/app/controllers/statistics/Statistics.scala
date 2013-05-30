package controllers.statistics

import controllers.OrganizationController
import play.api.mvc.Action
import models.{ Role, Group, OrganizationConfiguration, DataSet }
import collection.JavaConverters._
import models.statistics.DataSetStatistics
import collection.immutable.ListMap
import org.apache.solr.client.solrj.SolrQuery
import core.{ SystemField, CultureHubPlugin, Constants }
import org.apache.solr.client.solrj.response.FacetField.Count
import play.api.i18n.{ Lang, Messages }
import plugins.{ StatisticsPluginConfiguration, StatisticsPlugin }
import core.indexing.IndexField
import play.api.cache.Cache
import play.api.Play.current
import services.search.{ SolrQueryService, SolrBindingService }

/**
 * Prototype statistics plugin based on the statistics provided by the Sip-Creator.
 *
 * TODO configurable schema
 * TODO generify the fields we're most interested in, i.e. make some kind of interoperability mapping for them.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 */

object Statistics extends OrganizationController {

  def statistics(orgId: String) = OrganizationBrowsing {
    Action {
      implicit request =>
        if (getStatisticsConfig.map(_.public).getOrElse(false)) {
          Ok(Template("statistics.html"))
        } else {
          if (isMember && isAdmin) {
            Ok(Template("statistics.html"))
          } else {
            Unauthorized
          }
        }
    }
  }

  def legacyStatistics(orgId: String) = OrganizationConfigured {
    Action {
      implicit request =>

        val requestFacets = request.queryString.get("facet.field")
        val facetLimit = request.queryString.getOrElse("facet.limit", List("100")).head.toString.toInt
        val query = request.queryString.getOrElse("query", List("*:*")).head
        val facets: Map[String, String] = requestFacets.map { facet =>
          facet.map(f => (f -> f)).toMap
        }.getOrElse {
          getStatisticsConfig.map(_.facets).getOrElse {
            Map.empty
          }
        }

        val filter = request.queryString.get("filter").flatMap { f =>
          f.headOption
        }

        val canSeeFullStatistics = getStatisticsConfig.map(_.public).getOrElse(false) || request.session.get(Constants.USERNAME).map { userName =>
          Group.dao.hasRole(userName, StatisticsPlugin.UNIT_ROLE_STATISTICS_VIEW) || Group.dao.hasRole(userName, Role.OWN)
        }.getOrElse(false)

        // cache stats for 3 hours
        val statistics = Cache.getOrElse("facetStatistics-" + request.queryString.mkString.hashCode, 10800) {
          new SolrFacetBasedStatistics(orgId, facets, filter, facetLimit, query)
        }

        val result = if (request.queryString.getFirst("format") == Some("csv")) {
          Ok(statistics.renderAsCSV(canSeeFullStatistics)).as("text/csv")
        } else {
          Ok(statistics.renderAsJSON(canSeeFullStatistics)).as(JSON)
        }

        // CORS
        result.withHeaders(
          ("Access-Control-Allow-Origin" -> "*"),
          ("Access-Control-Allow-Methods" -> "GET, POST, OPTIONS"),
          ("Access-Control-Allow-Headers" -> "X-Requested-With"),
          ("Access-Control-Max-Age" -> "86400")
        )

    }
  }

  private def getStatisticsConfig(implicit configuration: OrganizationConfiguration): Option[StatisticsPluginConfiguration] = {
    CultureHubPlugin.getEnabledPlugins.find(_.pluginKey == "statistics").flatMap { p =>
      p.asInstanceOf[StatisticsPlugin].getStatisticsConfiguration
    }
  }

}

case class StatisticsCounter(name: String, total: Int, withNr: Int = 0) {
  private val percent = 100.0

  lazy val withPercentage: Long = math.round(withNr / (total / percent))
  lazy val withoutNr: Long = total - withNr
  lazy val withOutPercentage: Long = math.round(withoutNr / (total / percent))

}

case class CombinedStatisticEntry(name: String, total: Int, digitalObject: StatisticsCounter, landingPage: StatisticsCounter, geoRecords: StatisticsCounter) {

  def values = Seq(
    name,
    total,
    digitalObject.withNr,
    digitalObject.withPercentage,
    digitalObject.withoutNr,
    digitalObject.withOutPercentage,
    landingPage.withNr,
    landingPage.withPercentage,
    landingPage.withoutNr,
    landingPage.withOutPercentage,
    geoRecords.withNr,
    geoRecords.withPercentage,
    geoRecords.withoutNr,
    geoRecords.withOutPercentage
  )

  def asListMap = {
    ListMap(CombinedStatisticEntry.keys.zip(values): _*)
  }
}

object CombinedStatisticEntry {

  val keys = Seq(
    "name",
    "total",
    "digitalObjects",
    "digitalObjectsPercentage",
    "noDigitalObjects",
    "noDigitalObjectsPercentage",
    "landingPages",
    "landingPagesPercentage",
    "nolandingPages",
    "nolandingPagesPercentage",
    "GeoRecords",
    "GeoRecordsPercentage",
    "noGeoRecords",
    "noGeoRecordsPercentage"
  )

}

case class StatisticsHeader(name: String, label: String = "", entries: Seq[CombinedStatisticEntry]) {

  def asListMap = {
    ListMap(
      "name" -> name,
      "i18n" -> (if (label.isEmpty) name else label),
      "entries" ->
        entries.map(_.asListMap)
    )
  }
}

class SolrFacetBasedStatistics(orgId: String, facets: Map[String, String], filter: Option[String], facetLimit: Int = 100, queryString: String = "*:*")(implicit configuration: OrganizationConfiguration, lang: Lang) {

  val orgIdFilter = "%s:%s".format(IndexField.ORG_ID.key, orgId)

  // create list of facets you want returned
  val query = new SolrQuery
  // query for all *:* with facets
  query setQuery (queryString)
  query setFacet (true)
  query setFacetLimit (facetLimit)
  val facetsForStatistics = facets.keys.toSeq
  query addFacetField (facetsForStatistics: _*)
  query setRows (0)
  query setFilterQueries (orgIdFilter)
  filter foreach { f => query addFilterQuery f }

  val allRecordsResponse = SolrQueryService.getSolrResponseFromServer(solrQuery = query)
  val allRecords = SolrBindingService.createFacetStatistics(allRecordsResponse.getFacetFields.asScala.toList)
  val totalRecords = allRecordsResponse.getResults.getNumFound.toInt

  // query for with only digital objects
  query setFilterQueries ("%s:true".format(IndexField.HAS_DIGITAL_OBJECT.key), orgIdFilter)
  filter foreach { f => query addFilterQuery f }
  val digitalObjectsResponse = SolrQueryService.getSolrResponseFromServer(solrQuery = query)
  val digitalObjects = SolrBindingService.createFacetStatistics(digitalObjectsResponse.getFacetFields.asScala.toList)
  val totalDigitalObjects = digitalObjectsResponse.getResults.getNumFound

  // query with landing pages
  query setFilterQueries ("%s:true".format(IndexField.HAS_LANDING_PAGE.key), orgIdFilter)
  filter foreach { f => query addFilterQuery f }
  val landingPagesResponse = SolrQueryService.getSolrResponseFromServer(solrQuery = query)
  val landingPages = SolrBindingService.createFacetStatistics(landingPagesResponse.getFacetFields.asScala.toList)
  val totalLandingPages = landingPagesResponse.getResults.getNumFound

  // query with coordinates
  query setFilterQueries ("%s:true".format("delving_hasGeoHash"), orgIdFilter)
  filter foreach { f => query addFilterQuery f }
  val geoResponse = SolrQueryService.getSolrResponseFromServer(solrQuery = query)
  val geoRecords = SolrBindingService.createFacetStatistics(geoResponse.getFacetFields.asScala.toList)
  val totalGeoRecords = geoResponse.getResults.getNumFound

  def createHeader(facet: (String, String)): StatisticsHeader = {
    StatisticsHeader(
      name = facet._1,
      label = Messages(SolrBindingService.stripDynamicFieldLabels(facet._2)),
      entries = createEntries(facet._1)
    )
  }

  def getCountForFacet(name: String, facetList: List[Count]): Int = {
    val facetItem = facetList.find(count => count.getName.equalsIgnoreCase(name))
    if (facetItem == None) 0 else facetItem.get.getCount.toInt
  }

  def createEntries(name: String): Seq[CombinedStatisticEntry] = {
    val digitalObjectFacet = digitalObjects.getFacet(name)
    val landingPageFacet = landingPages.getFacet(name)
    val geoFacet = geoRecords.getFacet(name)

    allRecords.getFacet(name).map {
      count =>
        {
          CombinedStatisticEntry(
            name = count.getName,
            total = count.getCount.toInt,
            digitalObject = StatisticsCounter(name = count.getName, total = count.getCount.toInt, withNr = getCountForFacet(count.getName, digitalObjectFacet)),
            landingPage = StatisticsCounter(name = count.getName, total = count.getCount.toInt, withNr = getCountForFacet(count.getName, landingPageFacet)),
            geoRecords = StatisticsCounter(name = count.getName, total = count.getCount.toInt, withNr = getCountForFacet(count.getName, geoFacet))
          )
        }
    }
  }

  val entries: Seq[StatisticsHeader] = facets.map(createHeader(_)).toSeq

  val entryCounts: Map[String, Int] = entries.map { e =>
    (e.name -> e.entries.size)
  }.toMap

  val totalStatisticsEntry = {
    val entry = CombinedStatisticEntry(
      name = "total",
      total = totalRecords,
      digitalObject = StatisticsCounter(name = "totalDigitalObjects", total = totalRecords, withNr = totalDigitalObjects.toInt),
      landingPage = StatisticsCounter(name = "totalLangingPages", total = totalRecords, withNr = totalLandingPages.toInt),
      geoRecords = StatisticsCounter(name = "totalCoordinates", total = totalRecords, withNr = totalGeoRecords.toInt)
    )

    StatisticsHeader("total", "Total statistics", Seq(entry))
  }

  def renderAsJSON(displayFacetDetail: Boolean): String = {
    import net.liftweb.json.{ Extraction, JsonAST, Printer }
    implicit val formats = net.liftweb.json.DefaultFormats

    val outputJson = Printer.pretty(JsonAST.render(Extraction.decompose(
      ListMap("statistics" -> {
        val stats = ListMap(
          "totalRecords" -> totalRecords,
          "totalRecordsWithDigitalObjects" -> totalDigitalObjects,
          "totalRecordsWithLandingPages" -> totalLandingPages,
          "totalRecordsWithCoordinates" -> totalGeoRecords,
          "facetCounts" -> entryCounts
        )
        if (displayFacetDetail) {
          stats ++ ListMap("facets" -> entries.map(_.asListMap))
        } else {
          stats
        }
      }
      ))))

    outputJson
  }

  def renderAsCSV(displayFacetDetail: Boolean): String = {
    val header = Seq("facet", "label") ++ CombinedStatisticEntry.keys
    val displayedValues = if (displayFacetDetail) (Seq(totalStatisticsEntry) ++ entries) else Seq(totalStatisticsEntry)
    val values: Seq[Seq[String]] = (displayedValues flatMap { entry =>
      entry.entries map { statisticsEntry =>
        (Seq(
          entry.name,
          entry.label
        ) ++ statisticsEntry.values).map(_.toString)
      }
    })

    header.map(h => """"%s"""".format(h)).mkString(",") +
      "\n" +
      (values map { line: Seq[String] =>
        line.map(v => """"%s"""".format(v)).mkString(",")
      }).mkString("\n")
  }

}