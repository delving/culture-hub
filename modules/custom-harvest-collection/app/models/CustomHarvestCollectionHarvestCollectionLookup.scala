package models

import core.collection.{Harvestable, HarvestCollectionLookup}

/**
 * TODO move to correct plugin package
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CustomHarvestCollectionHarvestCollectionLookup extends HarvestCollectionLookup {

  def findAllNonEmpty(orgId: String, format: Option[String], accessKey: Option[String]): List[Harvestable] = {
    val vcs = VirtualCollection.dao(orgId).findAllNonEmpty(orgId)
    if (format.isDefined) {
      vcs.filter(vc => vc.getVisibleMetadataSchemas(accessKey).exists(_.prefix == format.get))
    } else {
      vcs
    }
  }

  def findBySpecAndOrgId(spec: String, orgId: String): Option[Harvestable] = VirtualCollection.dao(orgId).findBySpecAndOrgId(spec, orgId)

  def getAllMetadataFormats(orgId: String, accessKey: Option[String]): List[RecordDefinition] = List.empty
}
