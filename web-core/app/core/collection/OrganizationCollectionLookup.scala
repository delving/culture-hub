package core.collection

/**
 * Lookup for organization collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait OrganizationCollectionLookup {

  def findAll(orgId: String): Seq[OrganizationCollection]

  def findBySpecAndOrgId(spec: String, orgId: String): Option[OrganizationCollection]

}
