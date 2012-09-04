package services

import core.{ViewableRecord, RecordResolverService}
import eu.delving.schema.SchemaVersion
import core.Constants._
import models.{DomainConfiguration, DataSet, MetadataCache}

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
  def getRecord(hubId: String, schemaVersion: Option[SchemaVersion])(implicit configuration: DomainConfiguration): Option[ViewableRecord] = {

    val HubId(orgId, spec, localId) = hubId

    MetadataCache.get(orgId, spec, ITEM_TYPE_MDR).findOne(hubId).flatMap { record =>

      DataSet.dao.findBySpecAndOrgId(spec, orgId).map { collection =>

        val facts = collection.details.facts.asDBObject.map(kv => (kv._1.toString -> kv._2.toString))

        // TODO this is a workaround for not yet having a resolver for directory entries
        if (facts.contains("providerUri")) {
          facts.put("resolvedProviderUri", configuration.directoryService.providerDirectoryUrl +  facts("providerUri").split("/").reverse.head)
        }
        if (facts.contains("dataProviderUri")) {
          facts.put("resolvedDataProviderUri", configuration.directoryService.providerDirectoryUrl + facts("dataProviderUri").split("/").reverse.head)
        }

        val renderingSchema: Option[SchemaVersion] = {
          if (schemaVersion.isDefined) {
            schemaVersion
          } else {
            // use the indexing format as rendering format. if none is set try to find the first suitable one
            val inferredRenderingSchema = record.xml.keys.toList.intersect(configuration.schemas.toList).headOption
            val indexingSchema = collection.idxMappings.headOption.orElse(inferredRenderingSchema)
            if (indexingSchema.isDefined && record.schemaVersions.contains(indexingSchema.get)) {
              val version = record.schemaVersions(indexingSchema.get)
              Some(new SchemaVersion(indexingSchema.get, version))
            } else {
              None
            }
          }
        }

        renderingSchema.flatMap { s =>
          record.xml.get(s.getPrefix).map { recordXml =>
            ViewableRecord(recordXml, s, facts.toMap)
          }
        }
      }
    }.getOrElse {
      None
    }
  }
}
