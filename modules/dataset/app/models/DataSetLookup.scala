package models

import core.collection.{OrganizationCollection, OrganizationCollectionLookup, Harvestable, HarvestCollectionLookup}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSetLookup extends HarvestCollectionLookup with OrganizationCollectionLookup {

  def findAllNonEmpty(orgId: String, format: Option[String], accessKey: Option[String])(implicit configuration: DomainConfiguration): List[Harvestable] = {
    val sets = DataSet.dao(orgId).findAll().filterNot(_.state != DataSetState.ENABLED)
    if(format.isDefined) {
      sets.filter(ds => ds.getVisibleMetadataSchemas(accessKey).exists(_.prefix == format.get))
    } else {
      sets
    }
  }

  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: DomainConfiguration): Option[Harvestable] = DataSet.dao.findBySpecAndOrgId(spec, orgId)

  def getAllMetadataFormats(orgId: String, accessKey: Option[String])(implicit configuration: DomainConfiguration): List[RecordDefinition] = DataSet.dao.getAllVisibleMetadataFormats(orgId, accessKey).distinct

  def findAll(orgId: String)(implicit configuration: DomainConfiguration): Seq[OrganizationCollection] = DataSet.dao.findAll()

}
