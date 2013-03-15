package core

import core.collection.Harvestable
import models.{ OrganizationConfiguration, RecordDefinition }

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait HarvestCollectionLookupService {

  /**
   * Finds all harvest collections that have records.
   */
  def findAllNonEmpty(orgId: String, format: Option[String], accessKey: Option[String] = None)(implicit configuration: OrganizationConfiguration): Seq[Harvestable]

  /**
   * Finds a specific harvest collection
   */
  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: OrganizationConfiguration): Option[Harvestable]

  /**
   * Gets all publicly available formats out there, plus the ones available via the accessKey.
   */
  def getAllMetadataFormats(orgId: String, accessKey: Option[String])(implicit configuration: OrganizationConfiguration): Seq[RecordDefinition]

}
