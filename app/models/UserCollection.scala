package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import com.novus.salat._
import com.mongodb.casbah.Imports._
import org.joda.time.DateTime
import com.mongodb.casbah.commons.conversions.scala._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class UserCollection(_id: ObjectId = new ObjectId,
                          TS_update: DateTime,
                          user_id: ObjectId,
                          userName: String,
                          name: String,
                          thumbnail_object_id: Option[ObjectId] = None, // points to the object of which we take the thumbnail
                          node: String,
                          description: Option[String]) extends Repository {
}

object UserCollection extends SalatDAO[UserCollection, ObjectId](userCollectionsCollection) with Commons[UserCollection] with Resolver[UserCollection] with Pager[UserCollection] with AccessControl {

  RegisterJodaTimeConversionHelpers()

  protected def getCollection = userCollectionsCollection

  protected def getObjectIdField = "_id"

  def findAllByOwner(owner: UserReference) = {
    val userCollectionCursor = findAllByRight(owner.username, owner.node, "owner")
    (for(uc <- userCollectionCursor) yield grater[UserCollection].asObject(uc)).toList
  }

  def fetchName(id: String): String = findById(id).get.name

  def findAllWriteable(user: UserReference) = {
    val ownedCursor = findAllByRight(user.username, user.node, "owner")
    val updateCursor = findAllByRight(user.username, user.node, "update")
    val all = ownedCursor ++ updateCursor
    (for(uc <- all) yield grater[UserCollection].asObject(uc)).toList.distinct
  }

}