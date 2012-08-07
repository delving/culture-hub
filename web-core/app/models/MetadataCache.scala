package models

import _root_.util.DomainConfigurationHandler
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import mongoContext._
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
                       ) {

  def getRawXmlString = xml("raw")
}

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

  def iterate(index: Int = 0, limit: Option[Int]): Iterator[MetadataItem] = {
    val query = MongoDBObject("collection" -> col, "itemType" -> itemType) ++ ("index" $gt index)
    val cursor = find(query).sort(MongoDBObject("index" -> 1))
    if(limit.isDefined) {
      cursor.limit(limit.get)
    } else {
      cursor
    }
  }

  def list(index: Int = 0, limit: Option[Int]): List[MetadataItem] = iterate(index, limit).toList

  def count(): Long = count(MongoDBObject("collection" -> col, "itemType" -> itemType))

  def findOne(itemId: String): Option[MetadataItem] = findOne(MongoDBObject("collection" -> col, "itemType" -> itemType, "itemId" -> itemId))

  def remove(itemId: String) { remove(MongoDBObject("collection" -> col, "itemId" -> itemId)) }

  def removeAll() { remove(MongoDBObject("collection" -> col, "itemType" -> itemType)) }
}