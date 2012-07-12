package core.harvesting

import core.collection.{Harvestable}
import models.{RecordDefinition, DataSetState, DataSet}

/**
 * Lookup mechanism for Harvestable Collections
 *
 * TODO in the future, each harvestable collection type should register itself against this manager along with a way to retrieve the various lookups.
 * That way harvesting becomes a more modular functionality. At the moment all lookups are assembled here.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object AggregatingHarvestableCollectionLookup {

  def findAllNonEmpty(orgId: String, format: Option[String], accessKey: Option[String] = None): List[Harvestable] = {

    // TODO implement accessKey lookup
    val dataSets: List[Harvestable] = {
      val sets = DataSet.findAll(orgId).filterNot(_.state != DataSetState.ENABLED)
      if(format.isDefined) {
        sets.filter(ds => ds.getVisibleMetadataSchemas(accessKey).exists(_.prefix == format.get))
      } else {
        sets
      }
    }

    dataSets
  }

  def findBySpecAndOrgId(spec: String, orgId: String): Option[Harvestable] = {
    DataSet.findBySpecAndOrgId(spec, orgId)
  }

  /**
   * Gets all publicly available formats out there, plus the ones available via the accessKey.
   */
  def getAllMetadataFormats(orgId: String, accessKey: Option[String]): List[RecordDefinition] = {
    DataSet.getAllVisibleMetadataFormats(orgId, accessKey).distinct
  }


}
