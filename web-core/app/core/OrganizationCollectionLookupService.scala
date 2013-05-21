package core

import core.collection.OrganizationCollection

import models.OrganizationConfiguration

/**
 * Lookup for organization collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait OrganizationCollectionLookupService {

  /**
   * Find all collections
   */
  def findAll(implicit configuration: OrganizationConfiguration): Seq[OrganizationCollection]

  /**
   * Find a sepcific collection
   */
  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: OrganizationConfiguration): Option[OrganizationCollection]

}