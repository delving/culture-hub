package models

import _root_.util.DomainConfigurationHandler
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import HubMongoContext._
import com.novus.salat.dao.SalatDAO
import java.util.Date

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class MetadataItem(modified: Date = new Date(),
                        collection: String,
                        itemType: String,
                        itemId: String,
                        xml: Map[String, String], // prefix -> raw XML string
                        index: Int,
                        invalidTargetSchemas: Seq[String] = Seq.empty,
                        systemFields: Map[String, List[String]] = Map.empty
                       )

object MetadataCache {

  def getMongoCollectionName(orgId: String) = "%s_MetadataCache".format(orgId)

  def get(orgId: String, col: String, itemType: String): core.MetadataCache = {
    val configuration = DomainConfigurationHandler.getByOrgId(orgId)
    val mongoConnection = mongoConnections(configuration)
    val mongoCollection: MongoCollection = mongoConnection(getMongoCollectionName(configuration.orgId))
    mongoCollection.ensureIndex(MongoDBObject("collection" -> 1, "itemType" -> 1, "itemId" -> 1))
    mongoCollection.ensureIndex(MongoDBObject("collection" -> 1, "itemType" -> 1))
    mongoCollection.ensureIndex(MongoDBObject("collection" -> 1, "itemType" -> 1, "index" -> 1))

    new MongoMetadataCache(orgId, col, itemType, mongoCollection)
  }

}

class MongoMetadataCache(orgId: String, col: String, itemType: String, mongoCollection: MongoCollection) extends SalatDAO[MetadataItem, ObjectId](mongoCollection) with core.MetadataCache {

  def underlying: MongoMetadataCache = this

  def saveOrUpdate(item: MetadataItem) {
    val mappings = item.xml.foldLeft(MongoDBObject()) { (r, c) => r + (c._1 -> c._2) }
    update(
      MongoDBObject(
        "collection" -> item.collection, "itemType" -> item.itemType, "itemId" -> item.itemId),
        $set ("modified" -> new Date(), "collection" -> item.collection, "itemType" -> item.itemType, "itemId" -> item.itemId, "index" -> item.index, "systemFields" -> item.systemFields.asDBObject, "xml" -> mappings),
        true
    )
  }

  def iterate(index: Int = 0, limit: Option[Int], from: Option[Date] = None, until: Option[Date] = None): Iterator[MetadataItem] = {
    val query = MongoDBObject("collection" -> col, "itemType" -> itemType) ++ ("index" $gt index)
    val fromQuery = from.map { f => ("modified" $gte f) }
    val untilQuery = until.map { u => ("modified" $lte u) }

    val q = Seq(fromQuery, untilQuery).foldLeft(query) { (c, r) =>
      if (r.isDefined) c ++ r.get else c
    }

    val cursor = find(q).sort(MongoDBObject("index" -> 1))
    if(limit.isDefined) {
      cursor.limit(limit.get)
    } else {
      cursor
    }
  }

  def list(index: Int = 0, limit: Option[Int], from: Option[Date] = None, until: Option[Date] = None): List[MetadataItem] = iterate(index, limit, from, until).toList

  def count(): Long = count(MongoDBObject("collection" -> col, "itemType" -> itemType))

  def findOne(itemId: String): Option[MetadataItem] = findOne(MongoDBObject("collection" -> col, "itemType" -> itemType, "itemId" -> itemId))

  def remove(itemId: String) { remove(MongoDBObject("collection" -> col, "itemId" -> itemId)) }

  def removeAll() { remove(MongoDBObject("collection" -> col, "itemType" -> itemType)) }
}