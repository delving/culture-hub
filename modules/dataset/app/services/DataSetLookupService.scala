package services

import core.{HarvestCollectionLookupService, OrganizationCollectionLookupService}
import core.collection.{OrganizationCollection, Harvestable}
import models.{DomainConfiguration, RecordDefinition, DataSetState, DataSet}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSetLookupService extends HarvestCollectionLookupService with OrganizationCollectionLookupService {

  def findAllNonEmpty(orgId: String, format: Option[String], accessKey: Option[String])(implicit configuration: DomainConfiguration): List[Harvestable] = {
    val sets = DataSet.dao.findAll().filterNot(_.state != DataSetState.ENABLED)
    if (format.isDefined) {
      sets.filter(ds => ds.getVisibleMetadataSchemas(accessKey).exists(_.prefix == format.get))
    } else {
      sets
    }
  }

  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: DomainConfiguration): Option[Harvestable] = DataSet.dao.findBySpecAndOrgId(spec, orgId)

  def getAllMetadataFormats(orgId: String, accessKey: Option[String])(implicit configuration: DomainConfiguration): List[RecordDefinition] = DataSet.dao.getAllVisibleMetadataFormats(orgId, accessKey).distinct

  def findAll(implicit configuration: DomainConfiguration): Seq[OrganizationCollection] = DataSet.dao.findAll()

}
