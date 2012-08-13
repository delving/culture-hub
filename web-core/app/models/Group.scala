package models

import _root_.util.DomainConfigurationHandler
import core.access.{ResourceType, Resource}
import com.novus.salat.dao.SalatDAO
import mongoContext._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import core.HubServices

case class Group(_id: ObjectId = new ObjectId,
                 name: String,
                 orgId: String,
                 grantType: String,
                 resources: Seq[PersistedResource] = Seq.empty,
                 users: List[String] = List.empty[String])

object Group extends MultiModel[Group, GroupDAO] {

  def connectionName: String = "Groups"

  def initIndexes(collection: MongoCollection) { }

  def initDAO(collection: MongoCollection, connection: MongoDB): GroupDAO = new GroupDAO(collection)

}

class GroupDAO(collection: MongoCollection) extends SalatDAO[Group, ObjectId](collection) {

  /** lists all groups a user has access to for a given organization **/
  def list(userName: String, orgId: String) = {
    if(HubServices.organizationService(DomainConfigurationHandler.getByOrgId(orgId)).isAdmin(orgId, userName)) {
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

  def addResource(orgId: String, resourceKey: String, resourceType: ResourceType, groupId: ObjectId): Boolean = {
    findOneById(groupId).map { group =>
      val updated = group.copy(resources = group.resources ++ Seq(PersistedResource(resourceType.resourceType, resourceKey)))
      save(updated)
      true
    }.getOrElse(false)
  }

  def removeResource(orgId: String, resourceKey: String, resourceType: ResourceType, groupId: ObjectId): Boolean = {
    findOneById(groupId).map { group =>
      val updated = group.copy(resources = group.resources.filterNot(r => r.getResourceType == resourceType && r.getResourceKey == resourceKey))
      save(updated)
      true
    }.getOrElse(false)
  }

  def updateGroupInfo(groupId: ObjectId, name: String, grantType: Role): Boolean = {
    update(MongoDBObject("_id" -> groupId), $set("name" -> name, "grantType" -> grantType.key))
    true
  }

}

case class PersistedResource(resourceType: String, resourceKey: String) extends Resource {

  /** Kind of resource **/
  def getResourceType: ResourceType = ResourceType(resourceType)

  /** unique identifier of the resource **/
  def getResourceKey: String = resourceKey

}