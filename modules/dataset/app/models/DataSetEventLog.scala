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
                           transientEvent: Boolean = false
                          )

object DataSetEventLog extends MultiModel[DataSetEventLog, DataSetEventLogDAO] {

  val connectionName: String = "DataSetEventLog"

  def initIndexes(collection: MongoCollection) {
    addIndexes(collection, Seq(MongoDBObject("orgId" -> 1)))
    addIndexes(collection, Seq(MongoDBObject("transientEvent" -> 1)))
  }

  def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: DomainConfiguration): DataSetEventLogDAO = new DataSetEventLogDAO(collection)
}

class DataSetEventLogDAO(connection: MongoCollection) extends SalatDAO[DataSetEventLog, ObjectId](connection) {

  def findRecent = find(MongoDBObject()).limit(200).sort(MongoDBObject("_id" -> -1)).toList.reverse

  def removeTransient() {
    remove(MongoDBObject("transientEvent" -> true))
  }

}

