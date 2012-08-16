package controllers.statistics

import controllers.OrganizationController
import play.api.mvc.Action
import models.DataSet
import collection.JavaConverters._
import models.statistics.DataSetStatistics
import collection.immutable.ListMap
import core.search.FacetStatisticsMap

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

  def legacyStatistics(orgId: String, facet: String) = {
    Action {
      implicit request =>
      // get facet.fields from the requests
      // create list of facets you want returned
      // query for all *:* with facets
      // query for with only digital objects
      // query with landing pages
      // create combined query blocks per facet
      // add to list of StatisticsHeader
      // render list


        Ok().as(JSON)
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

case class StatisticsMap(facets: Seq[String], all: FacetStatisticsMap, digitalObjects: FacetStatisticsMap, landingPages: FacetStatisticsMap) {

//  def createHeader(name: String): StatisticsHeader = {
//    StatisticsHeader(
//      name = name,
//      entries = createEntries
//    )
//  }

  val entries: Seq[StatisticsHeader] = Seq.empty // todo replace late with facets.map(createHeader(_))

  def renderAsJSON(): String = {
    import net.liftweb.json.{Extraction, JsonAST, Printer}
    implicit val formats = net.liftweb.json.DefaultFormats

    val outputJson = Printer.pretty(JsonAST.render(Extraction.decompose(
      ListMap("Statistics" ->
        entries.map(_.asListMap)
      ))))

    outputJson
  }





}

/*todo output to be generated

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


