package core

import eu.delving.definitions.OrganizationEntry

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait DirectoryService {

  def findOrganization(query: String): List[OrganizationEntry]

  def findOrganizationByName(name: String): Option[OrganizationEntry]

}