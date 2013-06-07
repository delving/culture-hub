package controllers

import core._
import play.api.mvc._
import core.rendering.RecordRenderer
import com.mongodb.casbah.Imports._
import play.api.i18n.Messages
import core.Constants.SEARCH_TERM
import core.Constants.RETURN_TO_RESULTS
import com.escalatesoft.subcut.inject.BindingModule

/**
 * Renders the full view of an object
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class FullView(implicit val bindingModule: BindingModule) extends DelvingController {

  def render(orgId: String, spec: String, localId: String, format: Option[String]) = Root {
    Action {
      implicit request =>

        val hubId = HubId(configuration.orgId, spec, localId)

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

            if (renderedRecord.isRight) {

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

              // TODO what follows is a hack to compensate for Salat not retrieving nested Seq's correctly
              // TODO SystemFields should be replaced by a case class

              val titleField = r.systemFields.get("delving_title")
              val title: String = if (titleField.isDefined && titleField.get.isInstanceOf[scala.collection.immutable.List[String]]) {
                titleField.get.headOption.getOrElse("")
              } else if (titleField.isDefined) {
                val values = titleField.get.asInstanceOf[BasicDBList]
                if (values.size() > 0) values.get(0).toString else ""
              } else {
                ""
              }

              renderArgs += ("breadcrumbs" -> Breadcrumbs.crumble(
                Map(
                  "search" -> Map("searchTerm" -> searchTerm, "returnToResults" -> returnToResults),
                  "title" -> Map("url" -> "", "label" -> title),
                  "inOrg" -> Map("inOrg" -> "yes")
                )
              ))

              val returnToPrevious = r.resolveRefererLink.map { resolver =>
                val ref = request.headers.get(REFERER).getOrElse("")
                resolver(ref)
              }
              val returnToPreviousLink = returnToPrevious.map(_._1).getOrElse("")
              val returnToPreviousLabel = returnToPrevious.map(l => Messages(l._2)).getOrElse("")

              val snippets: Seq[(String, Unit)] = CultureHubPlugin.getEnabledPlugins.flatMap { plugin =>
                plugin.fullViewSnippet.map { snippet =>
                  (snippet._1 -> snippet._2(RequestContext(request, configuration, renderArgs, getLang), hubId))
                }
              }

              Ok(
                Template(
                  "Search/object.html",
                  'title -> title,
                  'systemFields -> r.systemFields,
                  'fullView -> renderedRecord.right.get.toViewTree,
                  'returnToResults -> returnToResults,
                  'returnToPreviousLink -> returnToPreviousLink,
                  'returnToPreviousLabel -> returnToPreviousLabel,
                  'orgId -> orgId,
                  'hubId -> hubId,
                  'rights -> r.parameters.get("rights").getOrElse(""),
                  'hasRelatedRecords -> r.hasRelatedItems,
                  'pluginIncludes -> snippets.map(_._1)
                )
              ).withSession(updatedSession)

            } else {
              NotFound("Record with ID %s could not be displayed: ".format(hubId) + renderedRecord.left.get)
            }

        }.getOrElse {
          NotFound("Record with ID %s could not be found".format(hubId))
        }
    }

  }

}