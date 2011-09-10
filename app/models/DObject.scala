package models

import org.bson.types.ObjectId
import salatContext._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.commons.conversions.scala._
import org.joda.time.DateTime
import com.novus.salat.dao.{SalatMongoCursor, SalatDAO}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DObject(_id: ObjectId = new ObjectId,
                  TS_update: DateTime,
                  user_id: ObjectId,
                  name: String,
                  description: Option[String] = None,
                  files: Seq[StoredFile] = Seq.empty[StoredFile],
                  thumbnail_id: Option[ObjectId] = None,
                  collections: List[ObjectId]) {

  // TODO this is computed at the moment but we probably should have a cache of userId -> fullname somewhere
  def userFullName = User.findOneByID(user_id).get.fullname
  def userName = User.findOneByID(user_id).get.reference.username

}

object DObject extends SalatDAO[DObject, ObjectId](objectsCollection) with Resolver[DObject] with Pager[DObject] {

  RegisterJodaTimeConversionHelpers()

  def findByUser(id: ObjectId) = find(MongoDBObject("user_id" -> id))

  // TODO index the collections field
  def findAllWithCollection(id: ObjectId) = find(MongoDBObject("collections" -> id))

  def findAllWithIds(ids: List[ObjectId]) = find(("_id" $in ids))

  def findAll() = find(MongoDBObject())

}