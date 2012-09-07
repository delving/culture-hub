package core.services

import core.collection.Harvestable
import models.{DomainConfiguration, RecordDefinition}
import core.{HarvestCollectionLookupService, CultureHubPlugin}

/**
 * Aggregated lookup mechanism for Harvest Collections.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class AggregatingHarvestCollectionLookup extends HarvestCollectionLookupService {


  def harvestCollectionLookups(implicit configuration: DomainConfiguration) = CultureHubPlugin.getServices(classOf[HarvestCollectionLookupService])

  def findAllNonEmpty(orgId: String, format: Option[String], accessKey: Option[String] = None)(implicit configuration: DomainConfiguration): Seq[Harvestable] = {
    harvestCollectionLookups.flatMap(lookup => lookup.findAllNonEmpty(orgId, format, accessKey))
  }

  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: DomainConfiguration): Option[Harvestable] = {
    harvestCollectionLookups.flatMap(_.findBySpecAndOrgId(spec, orgId)).headOption
  }

  /**
   * Gets all publicly available formats out there, plus the ones available via the accessKey.
   */
  def getAllMetadataFormats(orgId: String, accessKey: Option[String])(implicit configuration: DomainConfiguration): Seq[RecordDefinition] = {
    harvestCollectionLookups.flatMap(_.getAllMetadataFormats(orgId, accessKey))
  }


}
