package controllers

import play.api.mvc._
import models._
import core.Constants._
import core.search._
import exceptions._
import play.api.i18n.Messages
import core.rendering.{ViewType, RecordRenderer, ViewRenderer}
import com.mongodb.casbah.Imports._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object  Search extends DelvingController {

  // TODO move later
  lazy val viewRenderers: Map[DomainConfiguration, Map[String, ViewRenderer]] = RecordDefinition.enabledDefinitions.map {
    pair => {
      (pair._1 -> {
        pair._2.
          flatMap(f => ViewRenderer.fromDefinition(f, "html", pair._1)).
          map(r => (r.schema -> r)).toMap[String, ViewRenderer]
      })
    }
  }

  def index(query: String, page: Int) = search(query)

  def search(query: String = "*:*") = Root {
    Action {
      implicit request =>
        try {

          val solrQuery = request.queryString.getFirst("searchIn").map { searchIn =>
            if(searchIn == "all") {
              List(query)
            } else {
              List("""%s:"%s"""".format(searchIn, query))
            }
          }.getOrElse(List(query))

          val (items, briefItemView) = CommonSearch.search(Option(connectedUser), solrQuery)
          Ok(Template("/Search/index.html",
            'briefDocs -> items,
            'pagination -> briefItemView.getPagination,
            'facets -> briefItemView.getFacetQueryLinks,
            'themeFacets -> configuration.getFacets,
            'searchTerm -> query,
            'returnToResults -> request.rawQueryString)).withSession(
            session +
              (RETURN_TO_RESULTS -> request.rawQueryString) +
              (SEARCH_TERM -> query))
        } catch {
          case MalformedQueryException(s, t) => BadRequest(Template("/Search/invalidQuery.html", 'query -> query))
          case c: SolrConnectionException => Error(Messages("search.backendConnectionError"))
        }
      }

    }


  def record(orgId: String, spec: String, recordId: String, overlay: Boolean = false) = Root {
    Action {
      implicit request =>
        DataSet.dao.findBySpecAndOrgId(spec, orgId).map {
          collection =>
            val hubId = "%s_%s_%s".format(orgId, spec, recordId)

            MetadataCache.get(orgId, spec, ITEM_TYPE_MDR).findOne(hubId) match {
              case Some(mdr) =>

                val facts = collection.details.facts.asDBObject.map(kv => (kv._1.toString -> kv._2.toString))

                // TODO this is a workaround for not yet having a resolver for directory entries
                if (facts.contains("providerUri")) {
                  facts.put("resolvedProviderUri", configuration.directoryService.providerDirectoryUrl +  facts("providerUri").split("/").reverse.head)
                }
                if (facts.contains("dataProviderUri")) {
                  facts.put("resolvedDataProviderUri", configuration.directoryService.providerDirectoryUrl + facts("dataProviderUri").split("/").reverse.head)
                }

                val renderingSchema: Option[String] = {
                  // AFF takes precedence over anything else
                  if (mdr.xml.get("aff").isDefined) {
                    Some("aff")
                  } else {
                    // use the indexing format as rendering format. if none is set try to find the first suitable one
                    val inferredRenderingFormat = mdr.xml.keys.toList.intersect(RecordDefinition.enabledDefinitions(configuration).toList).headOption
                    val renderingFormat = collection.idxMappings.headOption.orElse(inferredRenderingFormat)
                    if (renderingFormat.isDefined && viewRenderers(configuration).contains(renderingFormat.get) && mdr.xml.contains(renderingFormat.get)) {
                      renderingFormat
                    } else {
                      None
                    }
                  }
                }

                if(renderingSchema.isEmpty) {
                  NotFound(Messages("rendering.notViewable", "This object cannot be displayed because no appropriate rendering schema could be found"))
                } else {
                  val renderResult = RecordRenderer.renderMetadataRecord(hubId, mdr.xml(renderingSchema.get), renderingSchema.get, renderingSchema.get, ViewType.HTML, getLang, false, Seq.empty, facts.toMap)

                  if(renderResult.isRight) {
                    val updatedSession = if (request.headers.get(REFERER) == None || !request.headers.get(REFERER).get.contains("search")) {
                      // we're coming from someplace else then a search, remove the return to results cookie
                      request.session - (RETURN_TO_RESULTS)
                    } else {
                      request.session
                    }

                    val returnToResults = updatedSession.get(RETURN_TO_RESULTS).getOrElse("")
                    val searchTerm = updatedSession.get(SEARCH_TERM).getOrElse("")

                    Ok(
                      Template(
                        "Search/object.html",
                        'systemFields -> mdr.systemFields,
                        'fullView -> renderResult.right.get.toViewTree,
                        'returnToResults -> returnToResults,
                        'searchTerm -> searchTerm,
                        'orgId -> orgId,
                        'hubId -> hubId,
                        'rights -> collection.getRights
                      )
                    ).withSession(updatedSession)

                  } else {
                    NotFound(Messages("rendering.notViewable", "Error during rendering: " + renderResult.left.get))
                  }


                }

              case None => NotFound("Record was not found")
            }
        }.getOrElse {
          NotFound("Collection was not found")
        }
    }
  }

}
