package models

import mongoContext._
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO

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


object DataSetEventLog extends SalatDAO[DataSetEventLog, ObjectId](dataSetEventLogCollection) {

  def findRecent = find(MongoDBObject()).limit(50).$orderby(MongoDBObject("_id" -> -1)).toList

}

