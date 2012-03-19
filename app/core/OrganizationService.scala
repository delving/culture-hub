package core

/**
 * Organization things
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait OrganizationService {

  def exists(orgId: String): Boolean

  def isAdmin(orgId: String, userName: String): Boolean

  def listAdmins(orgId: String): List[String]
  
  def addAdmin(orgId: String, userName: String): Boolean
  
  def removeAdmin(orgId: String, userName: String): Boolean

  def getName(orgId: String, language: String): Option[String]

}
