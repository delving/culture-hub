package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import com.novus.salat._
import com.mongodb.casbah.Imports._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class UserCollection(_id: ObjectId, name: String, node: String, description: Option[String], access: AccessRight) extends Repository

object UserCollection extends SalatDAO[UserCollection, ObjectId](collection = userCollectionsCollection) with AccessControl {

  protected def getCollection = userCollectionsCollection

  protected def getObjectIdField = "_id"

  def findAllByOwner(owner: UserReference) = {
    val userCollectionCursor = findAllByRight(owner.username, owner.node, "owner")
    (for(uc <- userCollectionCursor) yield grater[UserCollection].asObject(uc)).toList
  }

  def findAllWriteable(user: UserReference) = {
    val ownedCursor = findAllByRight(user.username, user.node, "owner")
    val updateCursor = findAllByRight(user.username, user.node, "update")
    val all = ownedCursor ++ updateCursor
    (for(uc <- all) yield grater[UserCollection].asObject(uc)).toList
  }

}