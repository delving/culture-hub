package models

import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import models.HubMongoContext._
import java.util.Date
import scala.util.matching.Regex

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class RouteAccess(_id: ObjectId = new ObjectId,
  date: Date = new Date(),
  uri: String,
  queryString: Map[String, Seq[String]])

object RouteAccess extends MultiModel[RouteAccess, RouteAccessDAO] {

  def connectionName: String = "RouteAccess"

  def initIndexes(collection: MongoCollection) {}

  def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: OrganizationConfiguration) = new RouteAccessDAO(collection)
}

class RouteAccessDAO(collection: MongoCollection) extends SalatDAO[RouteAccess, ObjectId](collection) {

  def findAfterForPath(date: Date, pathPattern: Regex) = find(("date" $gt date) ++ MongoDBObject("uri" -> pathPattern.pattern))

}