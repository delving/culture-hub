package core.harvesting

import core.collection.{Harvestable, CollectionManager}
import models.{RecordDefinition, VirtualCollection, DataSetState, DataSet}

/**
 * Lookup mechanism for Harvestable Collections
 *
 * TODO in the future, each harvestable collection type should register itself against this manager along with a way to retrieve the various lookups.
 * That way harvesting becomes a more modular functionality. At the moment all lookups are assembled here.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object HarvestableCollectionManager extends CollectionManager {

  def findAllNonEmpty(orgId: String, format: Option[String], accessKey: Option[String] = None): List[Harvestable] = {

    // TODO implement accessKey lookup
    val dataSets: List[Harvestable] ={
      val sets = DataSet.findAll(orgId).filterNot(_.state != DataSetState.ENABLED)
      if(format.isDefined) {
        sets.filter(ds => ds.getVisibleMetadataSchemas(accessKey).exists(_.prefix == format.get))
      } else {
        sets
      }
    }

    val virtualCollections: List[Harvestable] = {
      val vcs = VirtualCollection.findAllNonEmpty(orgId)
      if(format.isDefined) {
        vcs.filter(vc => vc.getVisibleMetadataFormats(accessKey).exists(_.prefix == format.get))
      } else {
        vcs
      }
    }

    dataSets ++ virtualCollections
  }

  def findBySpecAndOrgId(spec: String, orgId: String): Option[Harvestable] = {
    val maybeDataSet = DataSet.findBySpecAndOrgId(spec, orgId)
    if(maybeDataSet.isDefined) {
      val collection: Harvestable = maybeDataSet.get
      Some(collection)
    } else {
      val maybeVirtualCollection = VirtualCollection.findBySpecAndOrgId(spec, orgId)
      if(maybeVirtualCollection.isDefined) {
        val collection: Harvestable = maybeVirtualCollection.get
        Some(collection)
      } else {
        None
      }
    }
  }

  /**
   * Gets all publicly available formats out there, plus the ones available via the accessKey.
   */
  def getAllMetadataFormats(orgId: String, accessKey: Option[String]): List[RecordDefinition] = {
    DataSet.getAllVisibleMetadataFormats(orgId, accessKey).distinct
  }


}
