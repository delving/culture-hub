package models

import core.collection.{OrganizationCollection, OrganizationCollectionLookup, Harvestable, HarvestCollectionLookup}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSetLookup extends HarvestCollectionLookup with OrganizationCollectionLookup {

  def findAllNonEmpty(orgId: String, format: Option[String], accessKey: Option[String]): List[Harvestable] = {
    val sets = DataSet.dao(orgId).findAll().filterNot(_.state != DataSetState.ENABLED)
    if(format.isDefined) {
      sets.filter(ds => ds.getVisibleMetadataSchemas(accessKey).exists(_.prefix == format.get))
    } else {
      sets
    }
  }

  def findBySpecAndOrgId(spec: String, orgId: String): Option[Harvestable] = DataSet.dao(orgId).findBySpecAndOrgId(spec, orgId)

  def getAllMetadataFormats(orgId: String, accessKey: Option[String]): List[RecordDefinition] = DataSet.dao(orgId).getAllVisibleMetadataFormats(orgId, accessKey).distinct

  def findAll(orgId: String): Seq[OrganizationCollection] = DataSet.dao(orgId).findAll()

}
