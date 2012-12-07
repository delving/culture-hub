package models

import _root_.util.DomainConfigurationHandler
import com.mongodb.casbah.Imports._
import core.{ItemType, SystemField}
import org.bson.types.ObjectId
import HubMongoContext._
import com.novus.salat.dao.SalatDAO
import java.util.Date
import scala.collection.JavaConverters._

/**
 * A generic MetadataItem. The source data is meant to be kept as raw XML documents, for one or multiple versioned schemas.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class MetadataItem(modified: Date = new Date(),
                        collection: String,
                        itemType: String,
                        itemId: String,
                        xml: Map[String, String], // schemaPrefix -> raw XML string
                        schemaVersions: Map[String, String], // schemaPrefix -> schemaVersion
                        index: Int,
                        invalidTargetSchemas: Seq[String] = Seq.empty,
                        systemFields: Map[String, List[String]] = Map.empty
                       ) {

  def getSystemFieldValues(field: SystemField): Seq[String] = {
    systemFields.get(field.tag).getOrElse(new BasicDBList).asInstanceOf[BasicDBList].asScala.map(_.toString).toSeq
  }

}

object MetadataCache {

  def getMongoCollectionName(orgId: String) = "%s_MetadataCache".format(orgId)

  def get(orgId: String, col: String, itemType: ItemType): core.MetadataCache = get(orgId, col, itemType.itemType)

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
    val schemaVersions = item.schemaVersions.foldLeft(MongoDBObject()) { (r, c) => r + (c._1 -> c._2) }
    update(
        q = MongoDBObject("collection" -> item.collection, "itemType" -> item.itemType, "itemId" -> item.itemId),
        o = $set (
          "modified" -> new Date(),
          "collection" -> item.collection,
          "itemType" -> item.itemType,
          "itemId" -> item.itemId,
          "index" -> item.index,
          "systemFields" -> item.systemFields.asDBObject,
          "xml" -> mappings,
          "schemaVersions" -> schemaVersions
          ),
        upsert = true
    )
  }

  def iterate(index: Int = 0, limit: Option[Int], from: Option[Date] = None, until: Option[Date] = None): Iterator[MetadataItem] = {
    val query = MongoDBObject("collection" -> col, "itemType" -> itemType) ++ ("index" $gte index)
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

  def findMany(itemIds: Seq[String]): Seq[MetadataItem] = find(MongoDBObject("collection" -> col, "itemType" -> itemType) ++ ("itemId" $in itemIds)).toSeq

  def remove(itemId: String) { remove(MongoDBObject("collection" -> col, "itemId" -> itemId)) }

  def removeAll() { remove(MongoDBObject("collection" -> col, "itemType" -> itemType)) }
}