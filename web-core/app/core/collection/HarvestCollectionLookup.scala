package core.collection

import models.{DomainConfiguration, RecordDefinition}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait HarvestCollectionLookup {

  def findAllNonEmpty(orgId: String, format: Option[String], accessKey: Option[String] = None)(implicit configuration: DomainConfiguration): Seq[Harvestable]

  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: DomainConfiguration): Option[Harvestable]

  /**
   * Gets all publicly available formats out there, plus the ones available via the accessKey.
   */
  def getAllMetadataFormats(orgId: String, accessKey: Option[String])(implicit configuration: DomainConfiguration): Seq[RecordDefinition]

}
