package models

import org.bson.types.ObjectId
import salatContext._
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.SalatDAO
import controllers.dos.StoredFile
import java.util.Date

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DObject(_id: ObjectId = new ObjectId,
                   TS_update: Date,
                   user_id: ObjectId,
                   userName: String,
                   name: String,
                   description: String,
                   visibility: Visibility,
                   deleted: Boolean = false,
                   thumbnail_id: Option[ObjectId],
                   thumbnail_file_id: Option[ObjectId] = None, // pointer to the file selected as the thumbnail. This is _not_ helping to fetch the thumbnail, which is retrieved using the ID of the object
                   files: Seq[StoredFile] = Seq.empty[StoredFile],
                   collections: List[ObjectId],
                   labels: List[ObjectId]) extends Thing {

  // TODO this is computed at the moment but we probably should have a cache of userId -> fullname somewhere
  def userFullName = User.findOneByID(user_id).get.fullname

}

object DObject extends SalatDAO[DObject, ObjectId](objectsCollection) with Commons[DObject] with Resolver[DObject] with Pager[DObject] {

  // TODO index the collections field
  def findAllWithCollection(id: ObjectId) = find(MongoDBObject("collections" -> id))

  def findAllUnassignedForUser(id: ObjectId) = find(MongoDBObject("user_id" -> id, "collections" -> MongoDBObject("$size" -> 0)))

  def updateThumbnail(id: ObjectId, thumbnail_id: ObjectId) {
    update(MongoDBObject("_id" -> id), MongoDBObject("$set" -> MongoDBObject("thumbnail_file_id" -> thumbnail_id), "$set" -> MongoDBObject("thumbnail_id" -> id)) , false, false)
  }

  def fetchName(id: String): String = findById(id).get.name

  def removeFile(oid: ObjectId) {
    DObject.update(MongoDBObject(), (MongoDBObject("$pull" -> MongoDBObject("files" -> MongoDBObject("id" -> oid)))))
  }

}