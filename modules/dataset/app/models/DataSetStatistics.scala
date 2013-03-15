package models
package statistics

import _root_.util.OrganizationConfigurationHandler
import java.util.Date
import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import HubMongoContext._
import eu.delving.stats.Stats
import collection.JavaConverters._
import com.mongodb.casbah.Imports._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DataSetStatistics(_id: ObjectId = new ObjectId,
    context: DataSetStatisticsContext,
    recordCount: Int,
    fieldCount: Histogram) {

  def getStatisticsFile = {
    hubFileStores.getResource(OrganizationConfigurationHandler.getByOrgId(context.orgId)).findOne(MongoDBObject("orgId" -> context.orgId, "spec" -> context.spec, "uploadDate" -> context.uploadDate))
  }

  def getHistogram(path: String)(implicit configuration: OrganizationConfiguration): Option[Histogram] = DataSetStatistics.dao.frequencies.
    findByParentId(_id, MongoDBObject("context.orgId" -> context.orgId, "context.spec" -> context.spec, "context.uploadDate" -> context.uploadDate, "path" -> path)).toList.headOption.
    map(_.histogram)

}

case class FieldFrequencies(_id: ObjectId = new ObjectId,
  parentId: ObjectId,
  context: DataSetStatisticsContext,
  path: String,
  histogram: Histogram)

case class FieldValues(_id: ObjectId = new ObjectId,
  parentId: ObjectId,
  context: DataSetStatisticsContext,
  path: String,
  valueStats: ValueStats)

case class Histogram(present: Int,
  absent: Int,
  counterMap: Map[String, Counter] = Map.empty)

object Histogram {

  def apply(histogram: Stats.Histogram): Histogram = Histogram(
    present = histogram.present,
    absent = histogram.absent
  // TODO if we need this we have to think about how to store these things, since the values don't perform too well as keys
  //    counterMap = histogram.counterMap.asScala.map(h => (h._1 -> Counter(h._2.count, h._2.percentage, h._2.value, h._2.proportion))).toMap
  )
}

case class Counter(count: Int,
  percentage: String,
  value: String,
  proportion: Double)

case class ValueStats(total: Int,
  unique: Boolean,
  values: Option[Histogram],
  wordCounts: Option[Histogram])

object ValueStats {

  def apply(s: Stats.ValueStats): ValueStats = ValueStats(
    total = s.total,
    unique = if (s.unique == null) false else s.unique,
    values = Option(s.values).map(Histogram(_)),
    wordCounts = Option(s.wordCounts).map(Histogram(_))
  )
}

case class DataSetStatisticsContext(orgId: String,
  spec: String,
  schema: String,
  provider: String,
  dataProvider: String,
  providerUri: String,
  dataProviderUri: String,
  uploadDate: Date)

object DataSetStatistics extends MultiModel[DataSetStatistics, DataSetStatisticsDAO] {

  def connectionName: String = "DataSetStatistics"

  def initIndexes(collection: MongoCollection) {
    addIndexes(collection, dataSetStatisticsContextIndexes, dataSetStatisticsContextIndexNames)
  }

  def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: OrganizationConfiguration): DataSetStatisticsDAO = new DataSetStatisticsDAO(collection, connection)
}

class DataSetStatisticsDAO(collection: MongoCollection, connection: MongoDB) extends SalatDAO[DataSetStatistics, ObjectId](collection) {

  lazy val fieldFrequencies = connection("DataSetStatisticsFieldFrequencies")
  addIndexes(fieldFrequencies, dataSetStatisticsContextIndexes, dataSetStatisticsContextIndexNames)

  lazy val fieldValues = connection("DataSetStatisticsFieldValues")
  addIndexes(fieldValues, dataSetStatisticsContextIndexes, dataSetStatisticsContextIndexNames)

  val frequencies = new ChildCollection[FieldFrequencies, ObjectId](collection = fieldFrequencies, parentIdField = "parentId") {}

  val values = new ChildCollection[FieldValues, ObjectId](collection = fieldValues, parentIdField = "parentId") {}

  def getMostRecent(orgId: String, spec: String, schema: String) = find(MongoDBObject("context.orgId" -> orgId, "context.spec" -> spec, "context.schema" -> schema)).$orderby(MongoDBObject("_id" -> -1)).limit(1).toList.headOption

}