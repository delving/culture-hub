package core

import core.rendering.ViewType
import eu.delving.schema.SchemaVersion
import xml.NodeSeq
import models.DomainConfiguration

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
  def getRecord(hubId: HubId, schemaVersion: Option[SchemaVersion] = None)(implicit configuration: DomainConfiguration): Option[RenderableRecord]

}

case class RenderableRecord(recordXml: String,
                            systemFields: Map[String, List[String]],
                            schemaVersion: SchemaVersion,
                            viewType: ViewType = ViewType.HTML,
                            parameters: Map[String, String] = Map.empty)