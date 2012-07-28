package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import mongoContext._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import core.HubServices

case class Group(_id: ObjectId = new ObjectId,
                 node: String,
                 name: String,
                 orgId: String,
                 grantType: String,
                 dataSets: List[ObjectId] = List.empty[ObjectId],
                 users: List[String] = List.empty[String])

object Group extends MultiModel[Group, GroupDAO] {

  def connectionName: String = "Groups"

  def initIndexes(collection: MongoCollection) { }

  def initDAO(collection: MongoCollection, connection: MongoDB): GroupDAO = new GroupDAO(collection)

}

class GroupDAO(collection: MongoCollection) extends SalatDAO[Group, ObjectId](collection) {

  /** lists all groups a user has access to for a given organization **/
  def list(userName: String, orgId: String) = {
    if(HubServices.organizationService.isAdmin(orgId, userName)) {
      find(MongoDBObject("orgId" -> orgId))
    } else {
      find(MongoDBObject("users" -> userName, "orgId" -> orgId))
    }
  }

  def findDirectMemberships(userName: String, orgId: String) = find(MongoDBObject("orgId" -> orgId, "users" -> userName))

  def addUser(orgId: String, userName: String, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    HubUser.dao(orgId).update(MongoDBObject("userName" -> userName), $addToSet ("groups" -> groupId), false, false, WriteConcern.Safe)
    update(MongoDBObject("_id" -> groupId), $addToSet ("users" -> userName), false, false, WriteConcern.Safe)
    true
  }

  def removeUser(orgId: String, userName: String, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    HubUser.dao(orgId).update(MongoDBObject("userName" -> userName), $pull ("groups" -> groupId), false, false, WriteConcern.Safe)
    update(MongoDBObject("_id" -> groupId), $pull ("users" -> userName), false, false, WriteConcern.Safe)
    true
  }

  def addDataSet(id: ObjectId, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    update(MongoDBObject("_id" -> groupId), $addToSet ("dataSets" -> id), false, false, WriteConcern.Safe)
    true
  }

  def removeDataSet(id: ObjectId, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    update(MongoDBObject("_id" -> groupId), $pull ("dataSets" -> id), false, false, WriteConcern.Safe)
    true
  }

  def updateGroupInfo(groupId: ObjectId, name: String, grantType: GrantType): Boolean = {
    update(MongoDBObject("_id" -> groupId), $set("name" -> name, "grantType" -> grantType.key))
    true
  }

}