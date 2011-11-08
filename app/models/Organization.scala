package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import com.mongodb.casbah.Imports._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Organization(_id: ObjectId = new ObjectId,
                        node: String, // node on which this organization runs
                        orgId: String, // identifier of this organization, unique in the world, used in the URL
                        name: Map[String, String] = Map.empty[String, String], // language - orgName
                        users: List[String] = List.empty[String]) // member usernames

object Organization extends SalatDAO[Organization, ObjectId](organizationCollection) {

  def fetchName(orgId: String) = organizationCollection.findOne(MongoDBObject("orgId" -> orgId), MongoDBObject("name.en" -> 1)) match {
    case None => None
    case Some(org) => Some(org.getAs[String]("name.en"))
  }

  def findByOrgId(orgId: String) = Organization.findOne(MongoDBObject("orgId" -> orgId))
  def isOwner(userName: String) = Group.count(MongoDBObject("users" -> userName, "grantType.value" -> GrantType.OWN.value)) > 0

  def addUser(orgId: String, userName: String): Boolean = {
    // TODO FIXME make this operation safe
    Organization.update(MongoDBObject("orgId" -> orgId), $addToSet ("users" -> userName), false, false, SAFE_WC)
    User.update(MongoDBObject("userName" -> userName), $addToSet ("organizations" -> orgId), false, false, SAFE_WC)
    true
  }

  def removeUser(orgId: String, userName: String): Boolean = {
    // TODO FIXME make this operation safe
    Organization.update(MongoDBObject("orgId" -> orgId), $pull ("users" -> userName), false, false, SAFE_WC)
    User.update(MongoDBObject("userName" -> userName), $pull ("organizations" -> orgId), false, false, SAFE_WC)
    true
  }

}

case class Group(_id: ObjectId = new ObjectId,
                 node: String,
                 name: String,
                 orgId: String,
                 grantType: GrantType,
                 dataSets: List[ObjectId] = List.empty[ObjectId],
                 users: List[String] = List.empty[String])

object Group extends SalatDAO[Group, ObjectId](groupCollection) {

  /** lists all groups a user has access to for a given organization **/
  def list(userName: String, orgId: String) = {
    if(Organization.isOwner(userName)) {
      Group.find(MongoDBObject("orgId" -> orgId))
    } else {
      Group.find(MongoDBObject("users" -> userName, "orgId" -> orgId))
    }
  }

  def addUser(userName: String, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    User.update(MongoDBObject("userName" -> userName), $addToSet ("groups" -> groupId), false, false, SAFE_WC)
    Group.update(MongoDBObject("_id" -> groupId), $addToSet ("users" -> userName), false, false, SAFE_WC)
    true
  }

  def removeUser(userName: String, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    User.update(MongoDBObject("userName" -> userName), $pull ("groups" -> groupId), false, false, SAFE_WC)
    Group.update(MongoDBObject("_id" -> groupId), $pull ("users" -> userName), false, false, SAFE_WC)
    true
  }

  def addDataSet(id: ObjectId, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    Group.update(MongoDBObject("_id" -> groupId), $addToSet ("dataSets" -> id), false, false, SAFE_WC)
    true
  }

  def removeDataSet(id: ObjectId, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    Group.update(MongoDBObject("_id" -> groupId), $pull ("dataSets" -> id), false, false, SAFE_WC)
    true
  }

  def updateGroupInfo(groupId: ObjectId, name: String, grantType: Int): Boolean = {
    Group.update(MongoDBObject("_id" -> groupId), $set("name" -> name, "grantType.value" -> grantType))

    true
  }

}

case class GrantType(value: Int)
object GrantType {
  def illegal(value: Int) = throw new IllegalArgumentException("Illegal value %s for GrantType".format(value))
  val VIEW = GrantType(0)
  val MODIFY = GrantType(10)
  val OWN = GrantType(42)
  val values: Map[Int, String] = Map(VIEW.value -> "view", MODIFY.value -> "modify", OWN.value -> "own")
  def name(value: Int): String = values.get(value).getOrElse(illegal(value))
  def name(gt: GrantType): String = values.get(gt.value).getOrElse(illegal(gt.value))
  def get(value: Int) = {
    if(!values.contains(value)) illegal(value)
    GrantType(value)
  }

}