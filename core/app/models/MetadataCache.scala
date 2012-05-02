package models

import com.mongodb.casbah._
import commons.MongoDBObject
import org.bson.types.ObjectId
import core.MetadataCache
import mongoContext._
import com.novus.salat.dao.SalatDAO

/**
 *
 * for invalidTargetSchemas query: > db.Foo.find({items: {$ne: "ab"}})
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class MetadataItem(collection: String,
                        itemType: String,
                        itemId: String,
                        schemaPrefix: String, // aff, icn, crm, ...
                        rawXml: String,
                        insertionIndex: Int,
                        invalidTargetSchemas: Seq[String] = Seq.empty,
                        systemFields: Map[String, List[String]] = Map.empty
                       )

object MetadataCache {

  def getMongoCollectionName(orgId: String) = "%s_MetadataCache".format(orgId)

  def get(orgId: String): MetadataCache = {
    val collection = connection(getMongoCollectionName(orgId))
    collection.ensureIndex(MongoDBObject("collection" -> 1, "itemType" -> 1, "itemId" -> 1, "schemaPrefix" -> 1))

    object MongoMetadataCache extends SalatDAO[MetadataItem, ObjectId](collection) with MetadataCache {

      def saveOrUpdate(item: MetadataItem) {
        update(MongoDBObject("collection" -> item.collection, "itemType" -> item.itemType, "itemId" -> item.itemId, "schemaPrefix" -> item.schemaPrefix), _grater.asDBObject(item), false, true)
      }

    }

    MongoMetadataCache
  }


}