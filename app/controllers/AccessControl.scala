package controllers

import org.bson.types.ObjectId
import models.{Group, User}
import models.salatContext._
import com.mongodb.casbah.Imports._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object AccessControl {

  val ORGANIZATIONS = "organizations"
  val GROUPS = "groups"

  def addToGroup(user: ObjectId, group: ObjectId): Boolean = {
    User.update(MongoDBObject("_id" -> user), $addToSet ("groups" -> group))
    val userUpdated = Option(userCollection.lastError().get("updateExisting")) match {
      case None => false
      case Some(e) => e.asInstanceOf[Boolean]
    }
    Group.update(MongoDBObject("_id" -> group), $addToSet ("users" -> user))
    val groupUpdated = Option(groupCollection.lastError().get("updateExisting")) match {
      case None => false
      case Some(e) => e.asInstanceOf[Boolean]
    }
    userUpdated && groupUpdated
  }

}