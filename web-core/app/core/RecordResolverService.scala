package core

import eu.delving.schema.SchemaVersion
import xml.NodeSeq

/**
  *
  * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
  */
trait RecordResolverService {

  /**
   * Retrieves a record given a global hubId
   *
   * @param hubId the ID of the record
   * @param schemaVersion the (optional) version of the schema to be fetched
   */
  def getRecord(hubId: String, schemaVersion: Option[SchemaVersion] = None): Option[ViewableRecord]

}

case class ViewableRecord(recordXml: String, schemaVersion: SchemaVersion, relatedItems: Seq[NodeSeq], parameters: Map[String, String] = Map.empty)