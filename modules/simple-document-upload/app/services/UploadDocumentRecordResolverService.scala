package services

import core.{ CultureHubPlugin, RenderableRecord, HubId, RecordResolverService }
import eu.delving.schema.SchemaVersion
import models.{ MetadataCache, OrganizationConfiguration }
import plugins.{ SimpleDocumentUploadPlugin, SimpleDocumentUploadPluginConfiguration }
import controllers.organizations.SimpleDocumentUpload
import core.rendering.ViewType
import play.api.mvc.RequestHeader

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class UploadDocumentRecordResolverService extends RecordResolverService {

  /**
   * Retrieves a record given a global hubId
   *
   * @param hubId the ID of the record
   * @param schemaVersion the (optional) version of the schema to be fetched
   */
  def getRecord(hubId: HubId, schemaVersion: Option[SchemaVersion])(implicit request: RequestHeader, configuration: OrganizationConfiguration): Option[RenderableRecord] = {
    cache.findOne(hubId.id).flatMap { document =>
      val config = SimpleDocumentUploadPlugin.pluginConfiguration
      document.xml.get(config.schemaPrefix).map { recordXml =>
        RenderableRecord(recordXml, document.systemFields, new SchemaVersion(config.schemaPrefix, config.schemaVersion), ViewType.HTML)
      }
    }
  }

  private def cache(implicit configuration: OrganizationConfiguration) = {
    val collectionName = SimpleDocumentUploadPlugin.pluginConfiguration.collectionName
    MetadataCache.get(configuration.orgId, collectionName, SimpleDocumentUploadPlugin.ITEM_TYPE)
  }

}

