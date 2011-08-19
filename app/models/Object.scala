package models

import com.novus.salat.dao.SalatDAO
import org.bson.types.ObjectId
import salatContext._
import com.mongodb.casbah.commons.MongoDBObject

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Object(_id: ObjectId = new ObjectId, name: String, description: Option[String] = None, user: UserReference)

object Object extends SalatDAO[Object, ObjectId](collection = objectsCollection) {
  def findByUser(user: UserReference) = find(MongoDBObject("user.username" -> user.username, "user.node" -> user.node))
}