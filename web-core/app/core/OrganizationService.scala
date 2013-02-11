package core

import _root_.core.services.OrganizationProfile

/**
 * Organization things
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait OrganizationService {

  /**
   * Does the organization with this orgId alrady exist
   */
  def exists(orgId: String): Boolean

  /**
   * Finds organizations by orgId
   * @param query the orgId or a part thereof
   * @return a sequence of [[core.services.OrganizationProfile]]
   */
  def queryByOrgId(query: String): Seq[OrganizationProfile]

  /**
   * Is the user an administrator of the organiztion
   */
  def isAdmin(orgId: String, userName: String): Boolean

  /**
   * List all the administrators of this organization
   */
  def listAdmins(orgId: String): List[String]

  /**
   * Grant administration rights to a user
   */
  def addAdmin(orgId: String, userName: String): Boolean

  /**
   * Revoke administration rights of a user
   * @param orgId
   * @param userName
   * @return
   */
  def removeAdmin(orgId: String, userName: String): Boolean

  /**
   * Get the name of the organization given a language
   * @param orgId the organization ID
   * @param language the ISO two-letter code of the language
   */
  def getName(orgId: String, language: String): Option[String]

}
