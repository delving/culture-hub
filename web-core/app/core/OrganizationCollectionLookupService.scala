package core

import core.collection.OrganizationCollection

import models.DomainConfiguration

/**
 * Lookup for organization collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait OrganizationCollectionLookupService {

  def findAll(implicit configuration: DomainConfiguration): Seq[OrganizationCollection]

  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: DomainConfiguration): Option[OrganizationCollection]

}
