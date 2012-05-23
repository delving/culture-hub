package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import models.mongoContext._
import com.mongodb.casbah.commons.MongoDBObject

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class MusipItem(_id: ObjectId = new ObjectId, rawXml: String, orgId: String, itemId: String, itemType: String, systemFields: Map[String, List[String]] = Map.empty)

object MusipItem extends SalatDAO[MusipItem, ObjectId](collection = musipItemsCollection) {

  def saveOrUpdate(item: MusipItem, orgId: String, itemType: String) {
    MusipItem.update(MongoDBObject("orgId" -> orgId, "itemId" -> item.itemId, "itemType" -> itemType), _grater.asDBObject(item), true)
  }

  def find(orgId: String, itemId: String, itemType: String) = findOne(MongoDBObject("orgId" -> orgId, "itemId" -> itemId, "itemType" -> itemType))

}