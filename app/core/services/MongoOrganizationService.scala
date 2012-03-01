package core.services

import core.OrganizationService
import models.{HubUser, Organization}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class MongoOrganizationService extends OrganizationService {

  def exists(orgId: String) = Organization.findByOrgId(orgId).isDefined

  def isAdmin(orgId: String, userName: String) = Organization.isOwner(orgId, userName)

  def listAdmins(orgId: String): List[String] = Organization.findByOrgId(orgId).getOrElse(return List.empty).admins

  def addAdmin(orgId: String, userName: String) = Organization.addAdmin(orgId, userName)

  def removeAdmin(orgId: String, userName: String) = Organization.removeAdmin(orgId, userName)

  def listLocalMembers(orgId: String): List[String] = HubUser.listOrganizationMembers(orgId)

  def getName(orgId: String, language: String) = Organization.fetchName(orgId)
}
