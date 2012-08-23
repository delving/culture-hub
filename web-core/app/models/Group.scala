package models

import _root_.util.DomainConfigurationHandler
import core.access.{ResourceType, Resource}
import com.novus.salat.dao.SalatDAO
import HubMongoContext._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import core.HubServices
import play.api.i18n.Lang

case class Group(_id: ObjectId = new ObjectId,
                 name: String,
                 orgId: String,
                 roleKey: String,
                 resources: Seq[PersistedResource] = Seq.empty,
                 users: Seq[String] = Seq.empty[String]) {

  def description(lang: String)(implicit configuration: DomainConfiguration) = Role.get(roleKey).getDescription(Lang(lang))

}

object Group extends MultiModel[Group, GroupDAO] {

  def connectionName: String = "Groups"

  def initIndexes(collection: MongoCollection) { }

  def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: DomainConfiguration): GroupDAO = new GroupDAO(collection)

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

  def findResourceAdministrators(orgId: String, resourceType: ResourceType): Seq[String] = {
    Role.
            allRoles(DomainConfigurationHandler.getByOrgId(orgId)).
            filter(r => r.resourceType == Some(resourceType) && r.isResourceAdmin).
            flatMap(role => find(MongoDBObject("orgId" -> orgId, "roleKey" -> role.key))).
            flatMap(group => group.users).
            toSeq
  }

  def findUsersWithAccess(orgId: String, roleKey: String, resource: Resource): Seq[String] = {
    Role.
            allRoles(DomainConfigurationHandler.getByOrgId(orgId)).
            filter(_.key == roleKey).
            flatMap(role => find(MongoDBObject("orgId" -> orgId, "roleKey" -> role.key))).
            filter(group => group.resources.exists(p => p.getResourceKey == resource.getResourceKey && p.getResourceType == resource.getResourceType)).
            flatMap(group => group.users).
            toSeq
  }

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

  def updateGroupInfo(groupId: ObjectId, name: String, grantType: Role, users: Seq[String], resources: Seq[PersistedResource]): Boolean = {
    findOneById(groupId).map { group =>
      val updated = group.copy(
        name = name,
        roleKey = grantType.key,
        users = users,
        resources = resources)
      save(updated)
      true
    }.getOrElse(false)
  }

}

case class PersistedResource(resourceType: String, resourceKey: String) extends Resource {

  /** Kind of resource **/
  def getResourceType: ResourceType = ResourceType(resourceType)

  /** unique identifier of the resource **/
  def getResourceKey: String = resourceKey

}

object PersistedResource {

  def apply(r: Resource): PersistedResource = PersistedResource(r.getResourceType.resourceType, r.getResourceKey)
}