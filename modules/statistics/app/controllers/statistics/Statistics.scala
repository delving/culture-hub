package controllers.statistics

import controllers.OrganizationController
import play.api.mvc.Action
import core.Constants
import models.mongoContext._
import models.{MetadataCache, MongoMetadataCache, DataSet}
import com.mongodb.casbah.Imports._
import collection.JavaConverters._

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

            val total = ds.details.total_records
            val cache = new MongoMetadataCache(orgId, ds.spec, Constants.ITEM_TYPE_MDR, connection(MetadataCache.getMongoCollectionName(orgId)))
            val hasDigitalObject = cache.count(MongoDBObject("collection" -> ds.spec, "itemType" -> Constants.ITEM_TYPE_MDR, "systemFields.delving_hasDigitalObject" -> "true"))
            val hasNoLandingPage = cache.count(MongoDBObject("collection" -> ds.spec, "itemType" -> Constants.ITEM_TYPE_MDR, "systemFields.delving_landingPage" -> MongoDBObject("$size" -> 0)))

            Map(
              "spec" -> ds.spec,
              "total" -> total,
              "hasDigitalObjectCount" -> hasDigitalObject,
              "hasNoDigitalObjectCount" -> (total - hasDigitalObject),
              "hasLandingPageCount" -> (total - hasNoLandingPage),
              "hasNoLandingPageCount" -> hasNoLandingPage,
              "hasDigitalObjectPercentage" -> (if(total > 0) ((hasDigitalObject.toDouble / total) * 100).round else 0),
              "hasLandingPagePercentage" -> (if(total > 0) (((total.toDouble - hasNoLandingPage) / total) * 100).round else 0)
            )

          }
        }

        val dataSetStatistics = statistics.map(_.asJava).asJava

        Ok(Template("statistics.html", 'dataSetStatistics -> dataSetStatistics))
    }
  }

}
