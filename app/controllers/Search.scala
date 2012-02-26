package controllers

import play.api.mvc._
import models._
import util.Constants._
import views.Helpers._
import core.search._
import exceptions._
import play.api.i18n.Messages

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Search extends DelvingController {

  val RETURN_TO_RESULTS = "returnToResults"
  val SEARCH_TERM = "searchTerm"
  val IN_ORGANIZATION = "inOrg"

  def index(query: String, page: Int) = search(query, page)

  def search(query: String = "*:*", page: Int = 1, additionalSystemHQFs: List[String] = List.empty[String]) = Root {
    Action {
      implicit request =>
        val chQuery = SolrQueryService.createCHQuery(request, theme, true, Option(connectedUser), additionalSystemHQFs)
        try {
          val response = CHResponse(Params(request.queryString), theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery)
          val briefItemView = BriefItemView(response)
          val userCollections: List[ListItem] = if (isConnected) UserCollection.findByUser(connectedUser).toList else List()

          Ok(Template("/Search/index.html",
            'briefDocs -> briefItemView.getBriefDocs,
            'pagination -> briefItemView.getPagination,
            'facets -> briefItemView.getFacetQueryLinks,
            'collections -> userCollections,
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
        val id = "%s_%s_%s".format(orgId, spec, recordId)
        val idType = DelvingIdType(id, Params(request.queryString).all.getOrElse("idType", Seq("hubId")).head)
        val chQuery = SolrQueryService.createCHQuery(request, theme, false)
        val changedQuery = chQuery.copy(solrQuery = chQuery.solrQuery.setQuery("%s:\"%s\"".format(idType.idSearchField, idType.normalisedId)))
        val queryResponse = SolrQueryService.getSolrResponseFromServer(changedQuery.solrQuery, true)
        val response = CHResponse(Params(request.queryString), theme, queryResponse, changedQuery)

        if (response.response.getResults.size() == 0) {
          NotFound(id)
        } else {
          val updatedSession = if (request.headers.get(REFERER) == None || !request.headers.get(REFERER).get.contains("search")) {
            // we're coming from someplace else then a search, remove the return to results cookie
            request.session - (RETURN_TO_RESULTS)
          } else {
            request.session
          }

          val fullItemView = FullItemView(SolrBindingService.getFullDoc(queryResponse), queryResponse)
          if (overlay) {
            Ok(Template("Search/overlay.html", 'fullDoc -> fullItemView.getFullDoc))
          } else {
            val returnToResults = updatedSession.get(RETURN_TO_RESULTS).getOrElse("")
            val searchTerm = updatedSession.get(SEARCH_TERM).getOrElse("")
            Ok(Template("Search/object.html", 'fullDoc -> fullItemView.getFullDoc, 'returnToResults -> returnToResults, 'searchTerm -> searchTerm))
          }.withSession(updatedSession)
        }


    }
  }


  // ~~~ Utility methods (not controller actions)

  def browse(recordType: String, user: Option[String], page: Int, request: RequestHeader, theme: PortalTheme) = {
    search(user, page, request, theme, List("%s:%s".format(RECORD_TYPE, recordType)))
  }

  def search(user: Option[String], page: Int, request: RequestHeader, theme: PortalTheme, query: List[String]) = {
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
