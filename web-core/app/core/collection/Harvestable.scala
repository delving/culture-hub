package core.collection

import models.{RecordDefinition, MetadataItem}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Harvestable extends OrganizationCollection with CollectionInformation {

  def getNamespaces: Map[String, String]

  def getRecords(metadataFormat: String, position: Int, limit: Int): (List[MetadataItem], Long)

  def getVisibleMetadataFormats(accessKey: Option[String]): Seq[RecordDefinition]

}
