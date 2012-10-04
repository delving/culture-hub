package core

import core.collection.OrganizationCollection

import models.DomainConfiguration

/**
 * Lookup for organization collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait OrganizationCollectionLookupService {

  /**
   * Find all collections
   */
  def findAll(implicit configuration: DomainConfiguration): Seq[OrganizationCollection]

  /**
   * Find a sepcific collection
   */
  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: DomainConfiguration): Option[OrganizationCollection]

}
