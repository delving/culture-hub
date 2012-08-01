package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import mongoContext._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import play.api.Play
import play.api.Play.current
import core.HubServices

case class Group(_id: ObjectId = new ObjectId,
                 node: String,
                 name: String,
                 orgId: String,
                 grantType: String,
                 dataSets: List[ObjectId] = List.empty[ObjectId],
                 users: List[String] = List.empty[String])

object Group extends SalatDAO[Group, ObjectId](groupCollection) {

  /** lists all groups a user has access to for a given organization **/
  def list(userName: String, orgId: String) = {
    if(HubServices.organizationService.isAdmin(orgId, userName)) {
      Group.find(MongoDBObject("orgId" -> orgId))
    } else {
      Group.find(MongoDBObject("users" -> userName, "orgId" -> orgId))
    }
  }

  def findDirectMemberships(userName: String, orgId: String) = Group.find(MongoDBObject("orgId" -> orgId, "users" -> userName))

  def addUser(userName: String, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    HubUser.update(MongoDBObject("userName" -> userName), $addToSet ("groups" -> groupId), false, false, WriteConcern.Safe)
    Group.update(MongoDBObject("_id" -> groupId), $addToSet ("users" -> userName), false, false, WriteConcern.Safe)
    true
  }

  def removeUser(userName: String, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    HubUser.update(MongoDBObject("userName" -> userName), $pull ("groups" -> groupId), false, false, WriteConcern.Safe)
    Group.update(MongoDBObject("_id" -> groupId), $pull ("users" -> userName), false, false, WriteConcern.Safe)
    true
  }

  def addDataSet(id: ObjectId, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    Group.update(MongoDBObject("_id" -> groupId), $addToSet ("dataSets" -> id), false, false, WriteConcern.Safe)
    true
  }

  def removeDataSet(id: ObjectId, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    Group.update(MongoDBObject("_id" -> groupId), $pull ("dataSets" -> id), false, false, WriteConcern.Safe)
    true
  }

  def updateGroupInfo(groupId: ObjectId, name: String, grantType: GrantType): Boolean = {
    Group.update(MongoDBObject("_id" -> groupId), $set("name" -> name, "grantType" -> grantType.key))
    true
  }

}

case class GrantType(key: String, description: String, origin: String = "System")
object GrantType {

  def illegal(key: String) = throw new IllegalArgumentException("Illegal key %s for GrantType".format(key))

  def description(key: String) = play.api.i18n.Messages("org.group.grantType." + key)

  val VIEW = GrantType("view", description("view"))
  val MODIFY = GrantType("modify", description("modify"))
  val CMS = GrantType("cms", description("cms"))
  val OWN = GrantType("own", description("own"))

  val systemGrantTypes = List(VIEW, MODIFY, CMS, OWN)

  def dynamicGrantTypes = Role.getAllRoles

  val cachedGrantTypes = (systemGrantTypes ++ dynamicGrantTypes.map(r => GrantType(r.key, r.description, "Config")))

  def allGrantTypes = if(Play.isDev) {
    (systemGrantTypes ++ dynamicGrantTypes.map(r => GrantType(r.key, r.description, "Config")))
  } else {
    cachedGrantTypes
  }

  def get(grantType: String) = allGrantTypes.find(_.key == grantType).getOrElse(illegal(grantType))

}