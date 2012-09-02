package core.collection

import models.DomainConfiguration

/**
 * Lookup for organization collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait OrganizationCollectionLookup {

  def findAll(orgId: String)(implicit configuration: DomainConfiguration): Seq[OrganizationCollection]

  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: DomainConfiguration): Option[OrganizationCollection]

}
