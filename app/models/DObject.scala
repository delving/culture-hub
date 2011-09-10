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

}

object DObject extends SalatDAO[DObject, ObjectId](objectsCollection) with Resolver[DObject] {

  RegisterJodaTimeConversionHelpers()

  def findByUser(id: ObjectId) = find(MongoDBObject("user_id" -> id))

  // TODO index the collections field
  def findAllWithCollection(id: ObjectId) = find(MongoDBObject("collections" -> id))

  def findAllWithIds(ids: List[ObjectId]) = find(("_id" $in ids))

  def findAll() = find(MongoDBObject())

  implicit def cursorWithPage(cursor: SalatMongoCursor[DObject]) = new {

    /**
     * Returns a page and the total page count
     * @param page the page number
     * @param pageSize optional size of the page, defaults to 20
     */
    def page(page: Int, pageSize: Int = 20) = {
      val c = cursor.skip((page - 1) * pageSize).limit(20)
      (c.toList, c.count)
    }

  }

}