package core.collection

import models.{ RecordDefinition, MetadataItem }
import java.util.Date

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Harvestable extends OrganizationCollection with CollectionInformation {

  def getNamespaces: Map[String, String]

  def getRecords(metadataFormat: String, position: Int, limit: Int, from: Option[Date] = None, until: Option[Date] = None): (List[MetadataItem], Long)

  def getVisibleMetadataSchemas(accessKey: Option[String]): Seq[RecordDefinition]

}