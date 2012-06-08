package controllers

import play.api.mvc._
import models._
import core.Constants._
import core.search._
import exceptions._
import play.api.i18n.Messages
import core.rendering.ViewRenderer
import com.mongodb.casbah.Imports._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Search extends DelvingController {
  
  // TODO move later
  val affViewRenderer = ViewRenderer.fromDefinition("aff", "html")
  val icnViewRenderer = ViewRenderer.fromDefinition("icn", "full")

  def index(query: String, page: Int) = search(query, page)

  def search(query: String = "*:*", page: Int = 1, additionalSystemHQFs: List[String] = List.empty[String]) = Root {
    Action {
      implicit request =>
        val chQuery = SolrQueryService.createCHQuery(request, theme, true, Option(connectedUser), additionalSystemHQFs)
        try {
          val response = CHResponse(Params(request.queryString), theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery)
          val briefItemView = BriefItemView(response)

          Ok(Template("/Search/index.html",
            'briefDocs -> briefItemView.getBriefDocs,
            'pagination -> briefItemView.getPagination,
            'facets -> briefItemView.getFacetQueryLinks,
            'themeFacets -> theme.getFacets,
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
        DataSet.findBySpecAndOrgId(spec, orgId).map {
          collection =>
            val hubId = "%s_%s_%s".format(orgId, spec, recordId)

            MetadataCache.get(orgId, spec, ITEM_TYPE_MDR).findOne(hubId) match {
              case Some(mdr) =>

                val facts = collection.details.facts.asDBObject.map(kv => (kv._1.toString -> kv._2.toString))

                // TODO this is a workaround for not yet having a resolver for directory entries
                if(facts.contains("providerUri")) {
                  facts.put("resolvedProviderUri", "/%s/museum/%s".format(orgId, facts("providerUri").split("/").reverse.head))
                }
                if(facts.contains("dataProviderUri")) {
                  facts.put("resolvedDataProviderUri", "/%s/museum/%s".format(orgId, facts("dataProviderUri").split("/").reverse.head))
                }

                // TODO eventually make the selection mechanism dynamic, if we need to.
                // AFF takes precedence over anything else
                if(mdr.xml.get("aff").isDefined) {
                  val record = mdr.xml.get("aff").get
                  renderRecord(mdr, record, affViewRenderer.get, RecordDefinition.getRecordDefinition("aff").get, orgId, facts.toMap)
                } else if(mdr.xml.get("icn").isDefined) {
                  val record = mdr.xml.get("icn").get
                   renderRecord(mdr, record, icnViewRenderer.get, RecordDefinition.getRecordDefinition("icn").get, orgId, facts.toMap)
                } else {
                  NotFound(Messages("heritageObject.notViewable"))
                }

              case None => NotFound("Record was not found")
            }
        }.getOrElse {
          NotFound("Collection was not found")
        }
    }
  }

  private def renderRecord(mdr: MetadataItem, record: String, viewRenderer: ViewRenderer, definition: RecordDefinition, orgId: String, parameters: Map[String, String] = Map.empty)(implicit request: RequestHeader) = {

    val renderResult = viewRenderer.renderRecord(record, getUserGrantTypes(orgId), definition.getNamespaces, lang, parameters)

    val updatedSession = if (request.headers.get(REFERER) == None || !request.headers.get(REFERER).get.contains("search")) {
      // we're coming from someplace else then a search, remove the return to results cookie
      request.session - (RETURN_TO_RESULTS)
    } else {
      request.session
    }

    val returnToResults = updatedSession.get(RETURN_TO_RESULTS).getOrElse("")
    val searchTerm = updatedSession.get(SEARCH_TERM).getOrElse("")

    Ok(Template("Search/object.html", 'systemFields -> mdr.systemFields, 'fullView -> renderResult.toViewTree, 'returnToResults -> returnToResults, 'searchTerm -> searchTerm)).withSession(updatedSession)

  }


  // ~~~ Utility methods (not controller actions)

  def browse(recordType: String, user: Option[String], page: Int, theme: PortalTheme)(implicit request: RequestHeader) = {
    search(user, page, theme, List("%s:%s".format(RECORD_TYPE, recordType)))
  }

  def search(user: Option[String], page: Int, theme: PortalTheme, query: List[String])(implicit request: RequestHeader) = {
    val start = (page - 1) * PAGE_SIZE + 1
    val queryList = (user match {
      case Some(u) => List("%s:%s".format(OWNER, u))
      case None => List()
    }) ::: query
    val chQuery = SolrQueryService.createCHQuery(request, theme, false, Option(connectedUser), queryList)
    val queryResponse = SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true)
    val chResponse = CHResponse(Params(request.queryString + ("start" -> Seq(start.toString))), theme, queryResponse, chQuery)
    val briefItemView = BriefItemView(chResponse)

    val items = briefItemView.getBriefDocs.map(bd =>
      ListItem(id = bd.getMongoId,
        recordType = bd.getRecordType,
        title = bd.getTitle,
        description = bd.getDescription,
        thumbnailUrl = Some(bd.getThumbnailUri(220)),
        userName = bd.getOwnerId,
        isPrivate = bd.getVisibility.toInt == Visibility.PRIVATE.value,
        url = bd.getUri,
        mimeType = bd.getMimeType))

    (items, briefItemView.pagination.getNumFound)
  }


}
