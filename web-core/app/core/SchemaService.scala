package core

import models.DomainConfiguration
import eu.delving.schema.xml.Schema
import eu.delving.schema.SchemaType

/**
 * Gives access to the Schema repository
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait SchemaService {

  /**
   * Refreshes the repository, i.e. fetches the latest version of all schemas
   */
  def refresh()

  /**
   * Retrieves all Schemas that are active for the current configuration
   *
   * @param configuration the active DomainConfiguration
   * @return a sequence of Schema
   */
  def getSchemas(implicit configuration: DomainConfiguration): Seq[Schema]

  /**
   * Retrieves all Schemas the repository knows about
   * @return all schemas
   */
  def getAllSchemas: Seq[Schema]

  /**
   * Tries to retrieve the Schema content
   * @param prefix the schema prefidx
   * @param version the schema version
   * @param schemaType the schema type
   * @return an optional string with the schema contents
   */
  def getSchema(prefix: String, version: String, schemaType: SchemaType): Option[String]


}
