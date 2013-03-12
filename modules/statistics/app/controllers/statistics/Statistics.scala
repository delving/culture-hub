package controllers.statistics

import controllers.OrganizationController
import play.api.mvc.Action
import models.{ Role, Group, OrganizationConfiguration, DataSet }
import collection.JavaConverters._
import models.statistics.DataSetStatistics
import collection.immutable.ListMap
import core.search.{ SolrBindingService, SolrQueryService }
import org.apache.solr.client.solrj.SolrQuery
import core.{ SystemField, CultureHubPlugin, Constants }
import org.apache.solr.client.solrj.response.FacetField.Count
import play.api.i18n.{ Lang, Messages }
import plugins.{ StatisticsPluginConfiguration, StatisticsPlugin }
import core.indexing.IndexField
import play.api.cache.Cache
import play.api.Play.current

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
        val statistics = Cache.getOrElse("facetStatistics", 10800) {
          new SolrFacetBasedStatistics(orgId, facets, filter, facetLimit, query)
        }

        Ok(statistics.renderAsJSON(canSeeFullStatistics)).as(JSON)
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

  def asListMap = {
    ListMap(
      "name" -> name,
      "total" -> total,
      "digitalObjects" -> digitalObject.withNr,
      "digitalObjectsPercentage" -> digitalObject.withPercentage,
      "noDigitalObjects" -> digitalObject.withoutNr,
      "noDigitalObjectsPercentage" -> digitalObject.withOutPercentage,
      "landingPages" -> landingPage.withNr,
      "landingPagesPercentage" -> landingPage.withPercentage,
      "nolandingPages" -> landingPage.withoutNr,
      "nolandingPagesPercentage" -> landingPage.withOutPercentage,
      "GeoRecords" -> geoRecords.withNr,
      "GeoRecordsPercentage" -> geoRecords.withPercentage,
      "noGeoRecords" -> geoRecords.withoutNr,
      "noGeoRecordsPercentage" -> geoRecords.withOutPercentage
    )
  }
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
  query setFilterQueries ("%s:true]".format(SystemField.LANDING_PAGE.tag), orgIdFilter)
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

}