package models

import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import mongoContext._
import com.novus.salat.dao.SalatDAO
import core.MetadataCache
import java.util.Date

/**
 *
 * for invalidTargetSchemas query: > db.Foo.find({items: {$ne: "ab"}})
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

  def get(orgId: String, collection: String, itemType: String): MetadataCache = {
    val collection = connection(getMongoCollectionName(orgId))
    collection.ensureIndex(MongoDBObject("collection" -> 1, "itemType" -> 1, "itemId" -> 1))
    collection.ensureIndex(MongoDBObject("collection" -> 1, "itemType" -> 1))
    collection.ensureIndex(MongoDBObject("collection" -> 1, "itemType" -> 1, "index" -> 1))

    object MongoMetadataCache extends SalatDAO[MetadataItem, ObjectId](collection) with MetadataCache {

      def saveOrUpdate(item: MetadataItem) {
        update(MongoDBObject("collection" -> item.collection, "itemType" -> item.itemType, "itemId" -> item.itemId), _grater.asDBObject(item.copy(modified = new Date())), true)
      }

      def iterate(index: Int = 0, limit: Option[Int]): Iterator[MetadataItem] = {
        val query = MongoDBObject("collection" -> collection, "itemType" -> itemType) ++ ("index" $gt index)
        val cursor = find(query).sort(MongoDBObject("index" -> 1))
        if(limit.isDefined) {
          cursor.limit(limit.get)
        } else {
          cursor
        }
      }

      def list(index: Int = 0, limit: Option[Int]): List[MetadataItem] = iterate(index, limit).toList

      def count(): Long = count(MongoDBObject("collection" -> collection, "itemType" -> itemType))

      def findOne(itemId: String): Option[MetadataItem] = findOne(MongoDBObject("collection" -> collection, "itemType" -> itemType, "itemId" -> itemId))
    }

    MongoMetadataCache
  }


}