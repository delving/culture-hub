package models

import org.bson.types.ObjectId
import salatContext._
import com.mongodb.casbah.commons.MongoDBObject
import org.joda.time.DateTime
import com.novus.salat.dao.SalatDAO

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DObject(_id: ObjectId = new ObjectId,
                  TS_update: DateTime,
                  user_id: ObjectId,
                  userName: String,
                  visibility: Visibility.Value = Visibility.Private,
                  name: String,
                  description: Option[String] = None,
                  thumbnail_file_id: Option[ObjectId] = None, // pointer to the file selected as the thumbnail. This is _not_ helping to fetch the thumbnail, which is retrieved using the ID of the object
                  files: Seq[StoredFile] = Seq.empty[StoredFile],
                  collections: List[ObjectId],
                  labels: List[ObjectId]) {

  // TODO this is computed at the moment but we probably should have a cache of userId -> fullname somewhere
  def userFullName = User.findOneByID(user_id).get.fullname

}

object DObject extends SalatDAO[DObject, ObjectId](objectsCollection) with Commons[DObject] with Resolver[DObject] with Pager[DObject] {

  // TODO index the collections field
  def findAllWithCollection(id: ObjectId) = find(MongoDBObject("collections" -> id))

  def updateThumbnail(id: ObjectId, thumbnail_id: ObjectId) {
    update(MongoDBObject("_id" -> id), MongoDBObject("$set" -> MongoDBObject("thumbnail_id" -> thumbnail_id)) , false, false)
  }

}