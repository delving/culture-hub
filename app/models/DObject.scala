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
                  name: String,
                  description: Option[String] = None,
                  thumbnail_id: Option[ObjectId] = None,
                  files: Seq[StoredFile] = Seq.empty[StoredFile],
                  collections: List[ObjectId],
                  labels: List[ObjectId]) {

  // TODO this is computed at the moment but we probably should have a cache of userId -> fullname somewhere
  def userFullName = User.findOneByID(user_id).get.fullname

}

object DObject extends SalatDAO[DObject, ObjectId](objectsCollection) with Commons[DObject] with Resolver[DObject] with Pager[DObject] {

  // TODO index the collections field
  def findAllWithCollection(id: ObjectId) = find(MongoDBObject("collections" -> id))

}