package controllers

import core.{RecordResolverService, CultureHubPlugin, SchemaService, HubModule}
import play.api.mvc._
import core.rendering.{ViewType, RecordRenderer}

/**
 * Renders the full view of an object
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object FullView extends BoundController(HubModule) with FullView

trait FullView extends DelvingController { this: BoundController =>

  val schemaService = inject[SchemaService]

  def render(orgId: String, spec: String, localId: String, format: Option[String]) = Root {
    Action {
      implicit request =>

        val hubId = "%s_%s_%s".format(orgId, spec, localId)

        val resolvers = CultureHubPlugin.getServices(classOf[RecordResolverService])

        val record = resolvers.flatMap { r => r.getRecord(hubId) }.headOption

        record.map { r =>
          RecordRenderer.renderMetadataRecord(
            hubId = hubId,
            recordXml = r.recordXml,
            schema = r.schemaVersion,
            viewDefinitionFormatName = r.schemaVersion.getPrefix,
            viewType = ViewType.HTML,
            lang = lang,
            renderRelatedItems = true,
            relatedItems = r.relatedItems,
            parameters = r.parameters
          )
        }.getOrElse {
          NotFound("Record with ID %s could not be found".format(hubId))
        }

        Ok
    }

  }


}
