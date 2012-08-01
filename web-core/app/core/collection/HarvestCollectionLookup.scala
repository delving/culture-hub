package core.collection

import models.RecordDefinition

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait HarvestCollectionLookup {

  def findAllNonEmpty(orgId: String, format: Option[String], accessKey: Option[String] = None): List[Harvestable]

  def findBySpecAndOrgId(spec: String, orgId: String): Option[Harvestable]

  /**
   * Gets all publicly available formats out there, plus the ones available via the accessKey.
   */
  def getAllMetadataFormats(orgId: String, accessKey: Option[String]): List[RecordDefinition]

}
