package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import com.novus.salat._
import com.mongodb.casbah.Imports._
import java.util.Date

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class UserCollection(_id: ObjectId = new ObjectId,
                           TS_update: Date,
                           user_id: ObjectId,
                           username: String,
                           name: String,
                           description: String,
                           visibility: Visibility,
                           thumbnail_id: Option[ObjectId]) extends Thing

object UserCollection extends SalatDAO[UserCollection, ObjectId](userCollectionsCollection) with Commons[UserCollection] with Resolver[UserCollection] with Pager[UserCollection] with AccessControl {

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