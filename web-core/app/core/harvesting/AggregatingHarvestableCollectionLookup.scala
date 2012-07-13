package core.harvesting

import core.collection.Harvestable
import models.RecordDefinition
import core.CultureHubPlugin
import play.api.Play
import play.api.Play.current

/**
 * Aggregated lookup mechanism all for Harvestable Collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object AggregatingHarvestableCollectionLookup {

  lazy val hubPlugins: List[CultureHubPlugin] = Play.application.plugins.filter(_.isInstanceOf[CultureHubPlugin]).map(_.asInstanceOf[CultureHubPlugin]).toList
  lazy val harvestCollectionLookups = hubPlugins.flatMap(_.getHarvestCollectionLookups)

  def findAllNonEmpty(orgId: String, format: Option[String], accessKey: Option[String] = None): List[Harvestable] = {
    harvestCollectionLookups.flatMap(lookup => lookup.findAllNonEmpty(orgId, format, accessKey))
  }

  def findBySpecAndOrgId(spec: String, orgId: String): Option[Harvestable] = {
    harvestCollectionLookups.flatMap(_.findBySpecAndOrgId(spec, orgId)).headOption
  }

  /**
   * Gets all publicly available formats out there, plus the ones available via the accessKey.
   */
  def getAllMetadataFormats(orgId: String, accessKey: Option[String]): List[RecordDefinition] = {
    harvestCollectionLookups.flatMap(_.getAllMetadataFormats(orgId, accessKey))
  }


}
