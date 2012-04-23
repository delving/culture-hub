package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import mongoContext._
import java.util.Date
import com.mongodb.casbah.Imports._
import scala.util.matching.Regex
import collection.mutable.HashMap
import play.api.Logger

/**
 * Access statistics for organizations, collections, providers and data providers.
 * Each statistics type has its own mongo collection, and computed statistics entities are stored by key.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class ProviderStatistics(override val _id: ObjectId = new ObjectId,
                              orgId: String,
                              key: String,
                              uri: String = "",
                              override val lastUpdate: Date,
                              access: AccessStatistics = AccessStatistics(),
                              hits: SearchHitStatistics = SearchHitStatistics()) extends Statistics(_id, orgId, key, uri, lastUpdate, access, hits)

object ProviderStatistics extends SalatDAO[ProviderStatistics, ObjectId](providerStatisticsCollection) with StatisticsCommons[ProviderStatistics] {
  val apiPathMatcher: Regex = """^/organizations/([A-Za-z0-9-]+)/api/providers/([A-Za-z0-9-]+)/search*""".r

  def initialize(orgId: String, key: String) {
    ProviderStatistics.insert(ProviderStatistics(orgId = orgId, key = key, lastUpdate = new Date()))
  }
}

case class DataProviderStatistics(override val _id: ObjectId = new ObjectId,
                                  orgId: String,
                                  key: String,
                                  uri: String = "",
                                  override val lastUpdate: Date,
                                  access: AccessStatistics = AccessStatistics(),
                                  hits: SearchHitStatistics = SearchHitStatistics()) extends Statistics(_id, orgId, key, uri, lastUpdate, access, hits)

object DataProviderStatistics extends SalatDAO[DataProviderStatistics, ObjectId](dataProviderStatisticsCollection) with StatisticsCommons[DataProviderStatistics] {

  val apiPathMatcher: Regex = """^/organizations/([A-Za-z0-9-]+)/api/dataProviders/([A-Za-z0-9-]+)/search*""".r

  def initialize(orgId: String, key: String) {
    DataProviderStatistics.insert(DataProviderStatistics(orgId = orgId, key = key, lastUpdate = new Date()))
  }
}


case class OrganizationStatistics(override val _id: ObjectId = new ObjectId,
                                  orgId: String,
                                  key: String,
                                  uri: String = "",
                                  override val lastUpdate: Date,
                                  access: AccessStatistics = AccessStatistics(),
                                  hits: SearchHitStatistics = SearchHitStatistics()) extends Statistics(_id, orgId, key, uri, lastUpdate, access, hits)

object OrganizationStatistics extends SalatDAO[OrganizationStatistics, ObjectId](organizationStatisticsCollection) with StatisticsCommons[OrganizationStatistics] {

  val apiPathMatcher: Regex = """foo""".r

  def initialize(orgId: String, key: String) {
    OrganizationStatistics.insert(OrganizationStatistics(orgId = orgId, key = key, lastUpdate = new Date()))
  }

}


case class CollectionStatistics(override val _id: ObjectId = new ObjectId,
                                orgId: String,
                                key: String,
                                uri: String = "",
                                override val lastUpdate: Date,
                                access: AccessStatistics = AccessStatistics(),
                                hits: SearchHitStatistics = SearchHitStatistics()) extends Statistics(_id, orgId, key, uri, lastUpdate, access, hits)

object CollectionStatistics extends SalatDAO[CollectionStatistics, ObjectId](collectionStatisticsCollection) with StatisticsCommons[CollectionStatistics] {

  val apiPathMatcher: Regex = """^/organizations/([A-Za-z0-9-]+)/api/collections/([A-Za-z0-9-]+)/search*""".r

  def initialize(orgId: String, key: String) {
    CollectionStatistics.insert(CollectionStatistics(orgId = orgId, key = key, lastUpdate = new Date()))
  }

}


/**
 * Abstract definition of entity-based statistics
 */
abstract class Statistics(val _id: ObjectId,
                          orgId: String, // orgId for multi-tenancy
                          key: String, // name of the thing being accessed
                          uri: String, // URI, for later when we have one from commons
                          val lastUpdate: Date, // date of last computation
                          access: AccessStatistics,
                          hits: SearchHitStatistics)

// TODO add more things to be computed here: daily access, monthly access etc.
case class AccessStatistics(total: Int = 0)

// TODO add more things to be computed here: daily access, monthly access etc.
case class SearchHitStatistics(total: Int = 0)

/**a computation run **/
case class StatisticsRun(_id: ObjectId = new ObjectId)

object StatisticsRun extends SalatDAO[StatisticsRun, ObjectId](statisticsRunCollection) {

  def lastRun: Date = {
    val last = find(MongoDBObject()).$orderby(MongoDBObject("_id" -> -1)).toList.headOption
    last.map {
      run => new Date(run._id.getTime)
    }.getOrElse(new Date(0))
  }
}


/**
 * Helper trait to update statistics
 */
trait StatisticsCommons[A <: Statistics] {
  self: AnyRef with SalatDAO[A, ObjectId] =>

  // the path of an API route, the first match should be the orgId and the second one the type of the api access (provider, collection, dataProvider...)
  val apiPathMatcher: Regex

  def initialize(orgId: String, key: String): Unit

  def findAll = find(MongoDBObject())

  def computeAndSaveAccessStatistics(lastRun: Date) {

    var totals = new HashMap[(String, String), Int]()

    RouteAccess.findAfterForPath(lastRun, apiPathMatcher) foreach {
      access =>

        Logger("CultureHub").debug("Access statistics for access " + access.uri)

        val matcher = apiPathMatcher.pattern.matcher(access.uri)
        if(matcher.matches()) {
          val orgId = matcher.group(0)
          val apiType = matcher.group(1)

          // total
          if (totals.contains((orgId, apiType))) {
            // TODO there probably is a better data structure for this kind of sum stuff
            totals.put((orgId, apiType), totals((orgId, apiType)) + 1)
          } else {
            totals.put((orgId, apiType), 1)
          }
        }

    }

    // persist stats
    totals.keys.foreach {
      t: (String, String) => {
        val stat = findOne(MongoDBObject("orgId" -> t._1, "key" -> t._2))
        if(stat.isEmpty) {
          initialize(t._1, t._2)
        }

        update(MongoDBObject("_id" -> stat.get._id), $inc ("access.total" -> totals(t)))
      }

    }

  }

}

