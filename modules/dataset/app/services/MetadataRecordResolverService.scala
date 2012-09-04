package services

import core.{ViewableRecord, RecordResolverService}
import eu.delving.schema.SchemaVersion

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class MetadataRecordResolverService extends RecordResolverService {

  /**
   * Retrieves a record given a global hubId
   *
   * @param hubId the ID of the record
   * @param schemaVersion the (optional) version of the schema to be fetched
   */
  def getRecord(hubId: String, schemaVersion: Option[SchemaVersion]): Option[ViewableRecord] = {

    None

  }
}
