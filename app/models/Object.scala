package models

import com.novus.salat.dao.SalatDAO
import org.bson.types.ObjectId
import salatContext._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.commons.conversions.scala._
import org.joda.time.DateTime

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Object(_id: ObjectId = new ObjectId,
                  TS_update: DateTime,
                  name: String,
                  description: Option[String] = None,
                  user: UserReference,
                  collections: List[ObjectId])

object Object extends SalatDAO[Object, ObjectId](objectsCollection) {

  RegisterJodaTimeConversionHelpers()

  def findByUser(user: UserReference) = find(MongoDBObject("user.username" -> user.username, "user.node" -> user.node))
}