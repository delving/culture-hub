package core
import eu.delving.definitions.Organization

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait DirectoryService {

  def findOrganization(query: String): List[Organization]

  def findOrganizationByName(name: String): Option[Organization]

}
