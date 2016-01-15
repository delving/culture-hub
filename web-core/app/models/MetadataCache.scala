package models

import _root_.util.OrganizationConfigurationHandler
import com.mongodb.casbah.Imports._
import core.{ ItemType, SystemField }
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
    systemFields: Map[String, List[String]] = Map.empty) {

  def getSystemFieldValues(field: SystemField): Seq[String] = {
    systemFields.get(field.tag).getOrElse(new BasicDBList).asInstanceOf[BasicDBList].asScala.map(_.toString).toSeq
  }

}

object MetadataCache {

  def getMongoCollectionName(orgId: String) = "%s_MetadataCache".format(orgId)

  def get(orgId: String, col: String, itemType: ItemType): core.MetadataCache = get(orgId, col, itemType.itemType)

  def get(orgId: String, col: String, itemType: String): core.MetadataCache = {
    val configuration = OrganizationConfigurationHandler.getByOrgId(orgId)
    val mongoConnection = mongoConnections.getResource(configuration)

    // manu, 20.02.2012 - I'm fully aware that this may eventually lead to lost cached records
    //                    but a better fix involving batch inserts will eventually come later

    // manu, 16.03.2014 - commenting out the WriteConcern FsyncSafe as it leads to extremely slow writes (of the order of 100 ms / record)
    //        mongoConnection.setWriteConcern(WriteConcern.FsyncSafe)
    val mongoCollection: MongoCollection = mongoConnection(getMongoCollectionName(configuration.orgId))
    // manu, 16.03.2014 - commenting out the WriteConcern FsyncSafe as it leads to extremely slow writes (of the order of 100 ms / record)
    //    mongoCollection.setWriteConcern(WriteConcern.JournalSafe)
    mongoCollection.ensureIndex(MongoDBObject("collection" -> 1, "itemType" -> 1, "itemId" -> 1))
    mongoCollection.ensureIndex(MongoDBObject("collection" -> 1, "itemType" -> 1))
    mongoCollection.ensureIndex(MongoDBObject("collection" -> 1, "itemType" -> 1, "index" -> 1))

    new MongoMetadataCache(orgId, col, itemType, mongoCollection)
  }

  def getItemTypes(orgId: String, col: String): Seq[ItemType] = {
    val configuration = OrganizationConfigurationHandler.getByOrgId(orgId)
    val mongoConnection = mongoConnections.getResource(configuration)
    val mongoCollection: MongoCollection = mongoConnection(getMongoCollectionName(configuration.orgId))
    val types: Seq[Any] = mongoCollection.distinct("itemType", MongoDBObject("collection" -> col))
    types.map(t => ItemType(t.toString))
  }

}

class MongoMetadataCache(orgId: String, col: String, itemType: String, mongoCollection: MongoCollection) extends SalatDAO[MetadataItem, ObjectId](mongoCollection) with core.MetadataCache {

  def underlying: MongoMetadataCache = this

  def saveOrUpdate(item: MetadataItem) {
    val mappings = item.xml.foldLeft(MongoDBObject()) { (r, c) => r + (c._1 -> c._2) }
    val schemaVersions = item.schemaVersions.foldLeft(MongoDBObject()) { (r, c) => r + (c._1 -> c._2) }
    update(
      q = MongoDBObject("collection" -> item.collection, "itemType" -> item.itemType, "itemId" -> item.itemId),
      o = $set(
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

  def iterate(index: Int = 0, limit: Option[Int], from: Option[Date] = None, until: Option[Date] = None, metadataPrefix: Option[String] = None): Iterator[MetadataItem] = {
    // the use of index here is totally wrong when using metadataPrefix
    val query = MongoDBObject("collection" -> col, "itemType" -> itemType) ++ ("index" $gt index)
    val fromQuery = from.map { f => ("modified" $gte f) }
    val untilQuery = until.map { u => ("modified" $lte u) }
    val metadataPrefixQuery = metadataPrefix.map { prefix => (s"xml.$prefix" $exists true) }

    val q = Seq(fromQuery, untilQuery, metadataPrefixQuery).foldLeft(query) { (c, r) =>
      if (r.isDefined) c ++ r.get else c
    }

    val cursor = find(q).sort(MongoDBObject("index" -> 1))
    if (limit.isDefined) {
      cursor.limit(limit.get)
    } else {
      cursor
    }
  }

  def list(index: Int = 0, limit: Option[Int], from: Option[Date] = None, until: Option[Date] = None): List[MetadataItem] = iterate(index, limit, from, until).toList

  def listByMetadataPrefix(index: Int = 0, limit: Option[Int], from: Option[Date] = None, until: Option[Date] = None, metadataPrefix: Option[String] = None): List[MetadataItem] = iterate(index, limit, from, until, metadataPrefix).toList

  def count(): Long = count(MongoDBObject("collection" -> col, "itemType" -> itemType))

  def findOne(itemId: String): Option[MetadataItem] = findOne(MongoDBObject("collection" -> col, "itemType" -> itemType, "itemId" -> itemId))

  def findMany(itemIds: Seq[String]): Seq[MetadataItem] = find(MongoDBObject("collection" -> col, "itemType" -> itemType) ++ ("itemId" $in itemIds)).toSeq

  def remove(itemId: String) { remove(MongoDBObject("collection" -> col, "itemId" -> itemId)) }

  def removeAll() { remove(MongoDBObject("collection" -> col, "itemType" -> itemType)) }
}