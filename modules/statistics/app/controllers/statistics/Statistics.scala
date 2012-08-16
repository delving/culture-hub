package controllers.statistics

import controllers.OrganizationController
import play.api.mvc.Action
import models.{DomainConfiguration, DataSet}
import collection.JavaConverters._
import models.statistics.DataSetStatistics
import collection.immutable.ListMap
import core.search.{SolrBindingService, SolrQueryService, FacetStatisticsMap}
import org.apache.solr.client.solrj.SolrQuery
import core.Constants
import org.apache.solr.client.solrj.response.FacetField.Count

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

  def statistics(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>

        val statistics = DataSet.dao.findAll(orgId).map {
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

  def legacyStatistics(orgId: String) = {
    Action {
      implicit request =>
        val statistics = new SolrFacetBasedStatistics(request.queryString.get("facet.field"), orgId)
        Ok("{sjoerd: ok}").as(JSON) // later add statistics.renderAsJSON
    }
  }

}

case class StatisticsCounter(name: String, total: Int, withNr: Int = 0)  {

  lazy val withPercentage: Int = withNr / (total / 100)
  lazy val withoutNr: Int = total - withNr
  lazy val withOutPercentage: Int = withoutNr / (total / 100)

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
      "i18n" -> "plugin.statistics.%s".format(if (label.isEmpty) name else label),
      "entries" ->
        entries.map(_.asListMap)
    )
  }
}

class SolrFacetBasedStatistics(facets: Option[Seq[String]], orgId: String) (implicit configuration: DomainConfiguration) {

    val orgIdFilter = "%s:%s".format(Constants.ORG_ID, orgId)

    // create list of facets you want returned
    val query = new SolrQuery
    // query for all *:* with facets
    query setQuery ("*:*")
    query setFacet (true)
    val facetsForStatistics = if (facets != None) facets.get else List(Constants.OWNER)
    query addFacetField (facetsForStatistics: _*)
    query setRows (0)

    val allRecordsResponse = SolrQueryService.getSolrResponseFromServer(solrQuery = query)
    val allRecords = SolrBindingService.createFacetStatistics(allRecordsResponse.getFacetFields.asScala.toList)
    val totalRecords = allRecordsResponse.getResults.getNumFound.toInt

    // query for with only digital objects
    query setFilterQueries("%s:true".format(Constants.HAS_DIGITAL_OBJECT), orgIdFilter)
    val digitalObjectsResponse = SolrQueryService.getSolrResponseFromServer(solrQuery = query)
    val digitalObjects = SolrBindingService.createFacetStatistics(digitalObjectsResponse.getFacetFields.asScala.toList)
    val totalDigitalObjects = digitalObjectsResponse.getResults.getNumFound

    // query with landing pages
    query setFilterQueries("%s:[* TO *]".format(Constants.EXTERNAL_LANDING_PAGE))
    val landingPagesResponse = SolrQueryService.getSolrResponseFromServer(solrQuery = query)
    val landingPages = SolrBindingService.createFacetStatistics(landingPagesResponse.getFacetFields.asScala.toList)
    val totalLandingPages = landingPagesResponse.getResults.getNumFound


  def createHeader(name: String): StatisticsHeader = {
    StatisticsHeader(
      name = name,
      entries = createEntries(name)
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
          name = name,
          total = totalRecords,
          digitalObject = StatisticsCounter(name = name, total = totalRecords, withNr = getCountForFacet(name, digitalObjectFacet)),
          landingPage = StatisticsCounter(name = name, total = totalRecords, withNr = getCountForFacet(name, landingPageFacet))
        )
      }
    }
    Seq.empty
  }

  val entries: Seq[StatisticsHeader] = facetsForStatistics.map(createHeader(_))

  def renderAsJSON(): String = {
    import net.liftweb.json.{Extraction, JsonAST, Printer}
    implicit val formats = net.liftweb.json.DefaultFormats

    val outputJson = Printer.pretty(JsonAST.render(Extraction.decompose(
      ListMap("Statistics" ->
        ListMap(
          "totalRecords" -> totalRecords,
          "totalRecordsWithDigitalObjects" -> totalDigitalObjects,
          "totalRecordsWithLandingPages" -> totalLandingPages,
          "entries" -> entries.map(_.asListMap)
        )
      ))))

    outputJson
  }

}

/*

{
statistics: [
    {
    name: "municipality",
    i18n: "plugins.statistics.municipality",
    entries: [
            {
                name: "Norsk Folkemuseum",
                total: 359045,
                withDigitalObject: 299152,
                withDIgitalObjectPercentage: 81,
                withoutDigitalObject: 59893,
                withoutDigitalObjectPercentage: 19
            },
            {
                name: "Fylkesarkivet i Sogn og Fjordane",
                total: 248369,
                withDigitalObject: 220025,
                withDIgitalObjectPercentage: 85,
                withoutDigitalObject: 28344,
                withoutDigitalObjectPercentage: 15
            }
            ]
        },
        {
    name: "province",
    i18n: "plugins.statistics. province",
    entries: [                                                                         
            {
                name: "adfafwwere",
                total: 359045,
                withDigitalObject: 299152,
                withDIgitalObjectPercentage: 81,
                withoutDigitalObject: 59893,
                withoutDigitalObjectPercentage: 19
            },
            {
                name: "asdfwrewrwerewr",
                total: 248369,
                withDigitalObject: 220025,
                withDIgitalObjectPercentage: 85,
                withoutDigitalObject: 28344,
                withoutDigitalObjectPercentage: 15
            }
            ]
        }
    ]
}



*/


