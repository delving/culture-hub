package models

import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO
import models.HubMongoContext._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DataSetEventLog(_id: ObjectId = new ObjectId,
  orgId: String,
  spec: String,
  eventType: String,
  payload: Option[String],
  userName: Option[String],
  systemEvent: Boolean = false,
  transientEvent: Boolean = false)

object DataSetEventLog extends MultiModel[DataSetEventLog, DataSetEventLogDAO] {

  val connectionName: String = "DataSetEventLog"

  def initIndexes(collection: MongoCollection) {
    addIndexes(collection, Seq(MongoDBObject("orgId" -> 1)))
    addIndexes(collection, Seq(MongoDBObject("transientEvent" -> 1)))
  }

  def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: OrganizationConfiguration): DataSetEventLogDAO = new DataSetEventLogDAO(collection)
}

class DataSetEventLogDAO(connection: MongoCollection) extends SalatDAO[DataSetEventLog, ObjectId](connection) {

  def findRecent = {
    val nonTransient = find(MongoDBObject("transientEvent" -> false)).limit(10).sort(MongoDBObject("_id" -> -1)).toList
    val transient = find(MongoDBObject("transientEvent" -> true)).limit(40).sort(MongoDBObject("_id" -> -1)).toList

    (nonTransient ++ transient).sortBy(_._id.getTime).reverse
  }

  def removeTransient() {
    val recent = find(MongoDBObject("transientEvent" -> true)).limit(10).sort(MongoDBObject("_id" -> -1)).toList.reverse.headOption
    recent.map { r =>
      remove(MongoDBObject("transientEvent" -> true) ++ ("_id" $lt r._id))
    }.getOrElse {
      remove(MongoDBObject("transientEvent" -> true))
    }
  }

}