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
    // TODO FIXME make this operation safe
    User.update(MongoDBObject("_id" -> user), $addToSet ("groups" -> group), false, false, SAFE_WC)
    Group.update(MongoDBObject("_id" -> group), $addToSet ("users" -> user), false, false, SAFE_WC)
    true
  }

}