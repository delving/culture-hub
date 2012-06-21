package controllers.statistics

import controllers.OrganizationController
import play.api.mvc.Action
import models.DataSet
import collection.JavaConverters._
import models.statistics.DataSetStatistics

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Statistics extends OrganizationController {

  def statistics(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>

        val statistics = DataSet.findAll(orgId).map {
          ds => {

            DataSetStatistics.getMostRecent(ds.orgId, ds.spec).map {
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

}
