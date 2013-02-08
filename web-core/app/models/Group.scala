package models

import _root_.util.OrganizationConfigurationHandler
import core.access.{ResourceType, Resource}
import com.novus.salat.dao.SalatDAO
import HubMongoContext._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import core.{OrganizationService, DomainServiceLocator, HubModule}
import play.api.i18n.Lang
import org.scala_tools.subcut.inject.{Injectable, BindingModule}

case class Group(_id: ObjectId = new ObjectId,
                 name: String,
                 orgId: String,
                 roleKey: String,
                 resources: Seq[PersistedResource] = Seq.empty,
                 users: Seq[String] = Seq.empty[String],
                 systemGroup: Option[Boolean] = None) {

  def description(lang: String)(implicit configuration: OrganizationConfiguration) = Role.get(roleKey).getDescription(Lang(lang))

  def isSystemGroup = systemGroup.getOrElse(false)

}

object Group extends MultiModel[Group, GroupDAO] {

  def connectionName: String = "Groups"

  def initIndexes(collection: MongoCollection) { }

  def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: OrganizationConfiguration): GroupDAO =
    new GroupDAO(collection)(configuration, HubModule)

}

/**
 * Access rights management. Some of this should move to a better location, or be easier to find
 */
class GroupDAO(collection: MongoCollection)(implicit configuration: OrganizationConfiguration, val bindingModule: BindingModule)
  extends SalatDAO[Group, ObjectId](collection) with Injectable {

  val organizationServiceLocator = inject [ DomainServiceLocator[OrganizationService] ]


  // ~~~ role-based access rights

  /** finds all users that have access to a specific resource within a given role **/
  def findUsersWithAccess(orgId: String, role: Role, resource: Resource): Seq[String] = {
    Role.
            allPrimaryRoles(OrganizationConfigurationHandler.getByOrgId(orgId)).
            filter(_.key == role.key).
            flatMap(role => find(MongoDBObject("orgId" -> orgId, "roleKey" -> role.key))).
            filter(group => group.resources.exists(p => p.getResourceKey == resource.getResourceKey && p.getResourceType == resource.getResourceType)).
            flatMap(group => group.users).
            toSeq
  }

  /** whether a user is in any of the given roles **/
  def hasAnyRole(userName: String, roles: Seq[Role]) = roles.foldLeft(false) { (c, r) => c || hasRole(userName, r) }

  /** whether a user is in the given role **/
  def hasRole(userName: String, role: Role): Boolean = hasRole(userName, role.key)

  /** whether a user is in the given role **/
  def hasRole(userName: String, roleKey: String): Boolean = {
    val directGroupMemberships = findDirectMemberships(userName).toSeq
    val roles = directGroupMemberships.map(group => Role.get(roleKey))

    val isAdmin = roleKey == Role.OWN.key && organizationServiceLocator.byDomain.isAdmin(configuration.orgId, userName)

    isAdmin ||
    roles.exists(_.key == roleKey) ||
    roles.flatMap(_.unitRoles).exists(_.key == roleKey)
  }



  // ~~~ group lookups

  /** lists all groups a user has access to for a given organization **/
  def list(userName: String, orgId: String) = {
    if(organizationServiceLocator.byDomain.isAdmin(orgId, userName)) {
      find(MongoDBObject("orgId" -> orgId))
    } else {
      find(MongoDBObject("users" -> userName, "orgId" -> orgId))
    }
  }

  /** finds all groups of which the user is a direct member **/
  def findDirectMemberships(userName: String) = find(MongoDBObject("orgId" -> configuration.orgId, "users" -> userName))

  /** finds all administrators for a given ResourceType **/
  def findResourceAdministrators(orgId: String, resourceType: ResourceType): Seq[String] = {
    Role.
            allPrimaryRoles(OrganizationConfigurationHandler.getByOrgId(orgId)).
            filter(r => r.resourceType == Some(resourceType) && r.isResourceAdmin).
            flatMap(role => find(MongoDBObject("orgId" -> orgId, "roleKey" -> role.key))).
            flatMap(group => group.users).
            toSeq
  }

  // ~~~ group management

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