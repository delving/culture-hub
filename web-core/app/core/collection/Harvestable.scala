package core.collection

import models.{ RecordDefinition, MetadataItem }
import java.util.Date

/**
 * A collection that can be harvested via the OAI-PMH protocol
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Harvestable extends OrganizationCollection with CollectionMetadata {

  /**
   * The namespaces that the XML records in this collection make use of
   * @return
   */
  def getNamespaces: Map[String, String]

  /**
   * Fetches the records of the collection
   * @param metadataFormat the metadata format in which to get the records
   * @param position the index at which to start serving the records
   * @param limit the maximum number of records to return
   * @param from optional start date
   * @param until optional until date
   *
   * @return a tuple containing the list of [[ models.MetadataItem ]] as well as the total number of items to be expected
   */
  def getRecords(metadataFormat: String, position: Int, limit: Int, from: Option[Date] = None, until: Option[Date] = None): (List[MetadataItem], Long)

  /**
   * The metadata formats visible for this request
   * @param accessKey an optional accessKey that may give access to more formats
   * @return the set of formats represented by a [[ models.RecordDefinition ]]
   */
  def getVisibleMetadataSchemas(accessKey: Option[String]): Seq[RecordDefinition]

}