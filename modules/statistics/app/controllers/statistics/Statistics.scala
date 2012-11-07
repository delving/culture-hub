package controllers.statistics

import controllers.OrganizationController
import play.api.mvc.Action
import models.{Role, Group, DomainConfiguration, DataSet}
import collection.JavaConverters._
import models.statistics.DataSetStatistics
import collection.immutable.ListMap
import core.search.{SolrBindingService, SolrQueryService}
import org.apache.solr.client.solrj.SolrQuery
import core.{SystemField, CultureHubPlugin, Constants}
import org.apache.solr.client.solrj.response.FacetField.Count
import play.api.i18n.{Lang, Messages}
import plugins.StatisticsPlugin
import core.indexing.IndexField

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

  def statistics(orgId: String) = OrganizationAdmin {
    Action {
      implicit request =>

        val statistics = DataSet.dao.findAll().map {
          ds => {

            DataSetStatistics.dao.getMostRecent(ds.orgId, ds.spec, "icn").map {
              stats =>
                val total = stats.recordCount

                // TODO generify this by generic mapping of sorts
                val hasDigitalObject = stats.getHistogram("/icn:record/europeana:object").map(_.present).getOrElse(0)
                val hasLandingPage = stats.getHistogram("/icn:record/europeana:isShownAt").map(_.present).getOrElse(0)

                Map(
                  "spec" -> ds.spec,
                  "total" -> total,
                  "hasDigitalObjectCount" -> hasDigitalObject,
                  "hasNoDigitalObjectCount" -> (total - hasDigitalObject),
                  "hasLandingPageCount" -> hasLandingPage,
                  "hasNoLandingPageCount" -> (total - hasLandingPage),
                  "hasDigitalObjectPercentage" -> (if(total > 0) ((hasDigitalObject.toDouble / total) * 100).round else 0),
                  "hasLandingPagePercentage" -> (if(total > 0) (((hasLandingPage.toDouble) / total) * 100).round else 0)
                )
            }.getOrElse {
              Map(
                "spec" -> ds.spec,
                "total" -> "N/A",
                "hasDigitalObjectCount" -> "N/A",
                "hasNoDigitalObjectCount" -> "N/A",
                "hasLandingPageCount" -> "N/A",
                "hasNoLandingPageCount" -> "N/A",
                "hasDigitalObjectPercentage" -> "N/A",
                "hasLandingPagePercentage" -> "N/A"
              )
            }
          }
        }

        val dataSetStatistics = statistics.map(_.asJava).asJava

        Ok(Template("statistics.html", 'dataSetStatistics -> dataSetStatistics))
    }
  }

  /**
   *  The purpose of this fu
   */

  def legacyStatistics(orgId: String) = DomainConfigured {
      Action {
        implicit request =>

          val requestFacets = request.queryString.get("facet.field")
          val facetLimit = request.queryString.getOrElse("facet.limit", List("100")).head.toString.toInt
          val query = request.queryString.getOrElse("query", List("*:*")).head
          val facets: Map[String, String] = requestFacets.map { facet =>
            facet.map(f => (f -> f)).toMap
          }.getOrElse {
            CultureHubPlugin.getEnabledPlugins.find(_.pluginKey == "statistics").flatMap { p =>
              p.asInstanceOf[StatisticsPlugin].getStatisticsFacets
            }.getOrElse {
              Map.empty
            }
          }

          val filter = request.queryString.get("filter").flatMap { f =>
            f.headOption
          }

          val canSeeFullStatistics = request.session.get(Constants.USERNAME).map { userName =>
            Group.dao.hasRole(userName, StatisticsPlugin.UNIT_ROLE_STATISTICS_VIEW) || Group.dao.hasRole(userName, Role.OWN)
          }.getOrElse(false)

          val statistics = new SolrFacetBasedStatistics(orgId, facets, filter, facetLimit, query)
          Ok(statistics.renderAsJSON(canSeeFullStatistics)).as(JSON)
      }
    }

}

case class StatisticsCounter(name: String, total: Int, withNr: Int = 0)  {
  private val percent = 100.0

  lazy val withPercentage: Long = math.round(withNr / (total / percent))
  lazy val withoutNr: Long = total - withNr
  lazy val withOutPercentage: Long = math.round(withoutNr / (total / percent))

}

case class CombinedStatisticEntry(name: String, total: Int, digitalObject: StatisticsCounter, landingPage: StatisticsCounter) {

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
      "nolandingPagesPercentage" -> landingPage.withOutPercentage
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

class SolrFacetBasedStatistics(orgId: String, facets: Map[String, String], filter: Option[String], facetLimit: Int = 100, queryString: String = "*:*")(implicit configuration: DomainConfiguration, lang: Lang) {

    val orgIdFilter = "%s:%s".format(IndexField.ORG_ID.key, orgId)

    // create list of facets you want returned
    val query = new SolrQuery
    // query for all *:* with facets
    query setQuery (queryString)
    query setFacet (true)
    query setFacetLimit (facetLimit)
    val facetsForStatistics = facets.keys.toSeq
    query addFacetField (facetsForStatistics: _ *)
    query setRows (0)
    query setFilterQueries (orgIdFilter)
    filter foreach { f => query addFilterQuery f }

    val allRecordsResponse = SolrQueryService.getSolrResponseFromServer(solrQuery = query)
    val allRecords = SolrBindingService.createFacetStatistics(allRecordsResponse.getFacetFields.asScala.toList)
    val totalRecords = allRecordsResponse.getResults.getNumFound.toInt

    // query for with only digital objects
    query setFilterQueries("%s:true".format(IndexField.HAS_DIGITAL_OBJECT.key), orgIdFilter)
    filter foreach { f => query addFilterQuery f }
    val digitalObjectsResponse = SolrQueryService.getSolrResponseFromServer(solrQuery = query)
    val digitalObjects = SolrBindingService.createFacetStatistics(digitalObjectsResponse.getFacetFields.asScala.toList)
    val totalDigitalObjects = digitalObjectsResponse.getResults.getNumFound

    // query with landing pages
    query setFilterQueries("%s:[* TO *]".format(SystemField.LANDING_PAGE.tag), orgIdFilter)
    filter foreach { f => query addFilterQuery f }
    val landingPagesResponse = SolrQueryService.getSolrResponseFromServer(solrQuery = query)
    val landingPages = SolrBindingService.createFacetStatistics(landingPagesResponse.getFacetFields.asScala.toList)
    val totalLandingPages = landingPagesResponse.getResults.getNumFound


  def createHeader(facet: (String, String)): StatisticsHeader = {
    StatisticsHeader(
      name = facet._1,
      label = Messages(SolrBindingService.stripDynamicFieldLabels(facet._2)),
      entries = createEntries(facet._1)
    )
  }

  def getCountForFacet(name: String, facetList: List[Count]) : Int = {
    val facetItem = facetList.find(count => count.getName.equalsIgnoreCase(name))
    if (facetItem == None) 0 else facetItem.get.getCount.toInt
  }

  def createEntries(name: String): Seq[CombinedStatisticEntry] = {
    val digitalObjectFacet = digitalObjects.getFacet(name)
    val landingPageFacet = landingPages.getFacet(name)

    allRecords.getFacet(name).map{
      count => {
        CombinedStatisticEntry(
          name = count.getName,
          total = count.getCount.toInt,
          digitalObject = StatisticsCounter(name = count.getName, total = count.getCount.toInt, withNr = getCountForFacet(count.getName, digitalObjectFacet)),
          landingPage = StatisticsCounter(name = count.getName, total = count.getCount.toInt, withNr = getCountForFacet(count.getName, landingPageFacet))
        )
      }
    }
  }

  val entries: Seq[StatisticsHeader] = facets.map(createHeader(_)).toSeq

  val entryCounts: Map[String, Int] = entries.map { e =>
    (e.name -> e.entries.size)
  }.toMap

  def renderAsJSON(displayFacetDetail: Boolean): String = {
    import net.liftweb.json.{Extraction, JsonAST, Printer}
    implicit val formats = net.liftweb.json.DefaultFormats

    val outputJson = Printer.pretty(JsonAST.render(Extraction.decompose(
      ListMap("statistics" -> {
        val stats = ListMap(
          "totalRecords" -> totalRecords,
          "totalRecordsWithDigitalObjects" -> totalDigitalObjects,
          "totalRecordsWithLandingPages" -> totalLandingPages,
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