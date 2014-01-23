package services

import core.{ HubId, RenderableRecord, RecordResolverService }
import eu.delving.schema.SchemaVersion
import core.Constants._
import models.{ OrganizationConfiguration, DataSet, MetadataCache }
import com.mongodb.casbah.Imports._
import core.rendering.ViewType
import plugins.DataSetPlugin
import play.api.mvc.RequestHeader

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
  def getRecord(hubId: HubId, schemaVersion: Option[SchemaVersion])(implicit request: RequestHeader, configuration: OrganizationConfiguration): Option[RenderableRecord] = {

    MetadataCache.get(hubId.orgId, hubId.spec, DataSetPlugin.ITEM_TYPE).findOne(hubId.id).flatMap { record =>

      DataSet.dao.findBySpecAndOrgId(hubId.spec, hubId.orgId).map { collection =>

        val facts = collection.details.facts.map(kv => (kv._1.toString -> kv._2.toString))

        // TODO this is a workaround for not yet having a resolver for directory entries
        if (facts.contains("providerUri")) {
          facts.put("resolvedProviderUri", configuration.directoryService.providerDirectoryUrl + facts("providerUri").split("/").reverse.head)
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

        val availableSchemas = collection.getAllMappingSchemas

        renderingSchema.flatMap { s =>
          record.xml.get(s.getPrefix).map { recordXml =>
            RenderableRecord(
              recordXml = recordXml,
              systemFields = record.systemFields,
              schemaVersion = s,
              viewType = ViewType.HTML,
              parameters = facts.map(fact => fact._1 -> Seq(fact._2)).toMap,
              hasRelatedItems = true,
              resolveRefererLink = None,
              availableSchemas = availableSchemas.toList.map(f => f.toString))
          }
        }
      }
    }.getOrElse {
      None
    }
  }
}