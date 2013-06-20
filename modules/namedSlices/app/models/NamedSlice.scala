package models

import models.HubMongoContext._
import com.novus.salat.annotations._
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
case class NamedSlice(
  @Key("_id") id: ObjectId = new ObjectId,
  key: String,
  name: String,
  cmsPageKey: String,
  query: NamedSliceQuery,
  published: Boolean)

case class NamedSliceQuery(
  terms: String,
  dataSets: Seq[String] = Seq.empty)

object NamedSlice extends MultiModel[NamedSlice, NamedSliceDAO] {

  protected def connectionName: String = "NamedSlices"

  protected def initIndexes(collection: MongoCollection) {}

  protected def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: OrganizationConfiguration): NamedSliceDAO = new NamedSliceDAO(collection)
}

class NamedSliceDAO(collection: MongoCollection) extends SalatDAO[NamedSlice, ObjectId](collection) {

  def findOneByKey(key: String): Option[NamedSlice] = findOne(MongoDBObject("key" -> key))

  def findOnePublishedByKey(key: String): Option[NamedSlice] = findOne(MongoDBObject("key" -> key, "published" -> true))

  def findAllPublished: Seq[NamedSlice] = find(MongoDBObject("published" -> true)).toSeq

}