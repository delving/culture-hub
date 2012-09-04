package controllers

import core._
import play.api.mvc._
import core.rendering.{ViewType, RecordRenderer}
import core.Constants._
import com.mongodb.BasicDBList
import com.mongodb.casbah.Imports._
import play.api.i18n.Messages
import core.Constants.SEARCH_TERM
import core.Constants.RETURN_TO_RESULTS

/**
 * Renders the full view of an object
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object FullView extends BoundController(HubModule) with FullView

trait FullView extends DelvingController {
  this: BoundController =>

  val schemaService = inject[SchemaService]

  def render(orgId: String, spec: String, localId: String, format: Option[String]) = Root {
    Action {
      implicit request =>

        val hubId = HubId(orgId, spec, localId)

        val resolvers = CultureHubPlugin.getServices(classOf[RecordResolverService])

        val record = resolvers.flatMap {
          r => r.getRecord(HubId(orgId, spec, localId))
        }.headOption

        record.map {
          r =>

            val renderedRecord = RecordRenderer.renderMetadataRecord(
              hubId = hubId.id,
              recordXml = r.recordXml,
              schema = r.schemaVersion,
              viewDefinitionFormatName = r.schemaVersion.getPrefix,
              viewType = r.viewType,
              lang = lang,
              roles = getUserGrantTypes(orgId),
              parameters = r.parameters
            )

            val navigateFromSearch = request.headers.get(REFERER) != None && request.headers.get(REFERER).get.contains("search")
            val navigateFromRelatedItem = request.queryString.getFirst("mlt").getOrElse("false").toBoolean
            val updatedSession = if (!navigateFromSearch && !navigateFromRelatedItem) {
              // we're coming from someplace else then a search, remove the return to results cookie
              request.session - (RETURN_TO_RESULTS)
            } else {
              request.session
            }

            val returnToResults = updatedSession.get(RETURN_TO_RESULTS).getOrElse("")
            val searchTerm = updatedSession.get(SEARCH_TERM).getOrElse("")

            val fields = r.systemFields.get("delving_title").getOrElse(new BasicDBList).asInstanceOf[BasicDBList]

            renderArgs += ("breadcrumbs" -> Breadcrumbs.crumble(
              Map(
                "search" -> Map("searchTerm" -> searchTerm, "returnToResults" -> returnToResults),
                "title" -> Map("url" -> "", "label" -> Option(fields.get(0)).getOrElse("").toString),
                "inOrg" -> Map("inOrg" -> "yes")
              )
            ))

            Ok(
              Template(
                "Search/object.html",
                'systemFields -> r.systemFields,
                'fullView -> renderedRecord.right.get.toViewTree,
                'returnToResults -> returnToResults,
                'orgId -> orgId,
                'hubId -> hubId,
                'rights -> r.parameters.get("rights").getOrElse("")
              )
            ).withSession(updatedSession)


        }.getOrElse {
          NotFound("Record with ID %s could not be found".format(hubId))
        }
    }

  }


}