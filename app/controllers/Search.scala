package controllers

import scala.collection.JavaConversions._
import search._
import play.mvc.results.Result
import play.mvc.Http.Request
import org.bson.types.ObjectId
import play.mvc.Util
import models.{PortalTheme, Visibility, UserCollection}
import views.context.PAGE_SIZE
import util.Constants._

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 8/10/11 2:40 PM  
 */

object Search extends DelvingController {

  val RETURN_TO_RESULTS = "returnToResults"
  val SEARCH_TERM = "searchTerm"

  def index(query: String = "*:*", page: Int = 1) = {
    val chQuery = SolrQueryService.createCHQuery(request, theme, true, Option(connectedUser))
    val response = CHResponse(params, theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery)
    val briefItemView = BriefItemView(response)
    session.put(RETURN_TO_RESULTS, request.querystring)
    session.put(SEARCH_TERM, request.params.get("query"))

    val userCollections: List[ListItem] = if(isConnected) UserCollection.findByUser(connectedUser).toList else List()

    Template('briefDocs -> briefItemView.getBriefDocs, 'pagination -> briefItemView.getPagination, 'facets -> briefItemView.getFacetQueryLinks, 'collections -> userCollections, 'themeFacets -> theme.getFacets)
  }

  def record(orgId: String, spec: String, recordId: String, overlay: Boolean = false): Result = {
    val id = "%s_%s_%s".format(orgId, spec, recordId)
    val idType = DelvingIdType(id, params.all().getOrElse("idType", Array[String]("hubId")).head)
    val chQuery = SolrQueryService.createCHQuery(request, theme, false)
    val changedQuery = chQuery.copy(solrQuery = chQuery.solrQuery.setQuery("%s:%s".format(idType.idSearchField, idType.normalisedId)))
    val queryResponse = SolrQueryService.getSolrResponseFromServer(changedQuery.solrQuery, true)
    val response = CHResponse(params, theme, queryResponse, changedQuery)

    if (response.response.getResults.size() == 0)
      return NotFound(id)

    val fullItemView = FullItemView(SolrBindingService.getFullDoc(queryResponse), queryResponse)
    if (overlay) {
      Template("/Search/overlay.html", 'fullDoc -> fullItemView.getFullDoc)
    } else {
      // TODO check the request referrer header and only do perform the session lookup when coming from the result list page
      // otherwise, clear the session cache
      val returnToUrl = if (session.contains(RETURN_TO_RESULTS)) session.get(RETURN_TO_RESULTS) else ""
      Template("/Search/object.html", 'fullDoc -> fullItemView.getFullDoc, 'returnToResults -> returnToUrl)
    }
  }

  @Util def browse(recordType: String, user: Option[String], request: Request, theme: PortalTheme) = {
    search(user, request, theme, List("%s:%s".format(RECORD_TYPE, recordType)))
  }

  @Util def search(user: Option[String], request: Request, theme: PortalTheme, query: List[String]) = {
    val start = (Option(request.params.get("page")).getOrElse("1").toInt - 1) * PAGE_SIZE + 1
    request.params.put("start", start.toString)
    val queryList = (user match {
      case Some(u) => List("%s:%s".format(OWNER, u))
      case None => List()
    }) ::: query
    val chQuery = SolrQueryService.createCHQuery(request, theme, false, Option(connectedUser), queryList)
    val queryResponse = SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true)
    val chResponse = CHResponse(params, theme, queryResponse, chQuery)
    val briefItemView = BriefItemView(chResponse)

    val items = briefItemView.getBriefDocs.map(bd => ListItem(id = bd.getThingId, title = bd.getTitle, description = bd.getDescription, thumbnail = bd.getThumbnailDirect match {
      case id if ObjectId.isValid(id) => Some(new ObjectId(id))
      case _ => None
    }, userName = bd.getOwnerId, fullUserName = "", isPrivate = bd.getVisibility.toInt == Visibility.PRIVATE, url = bd.getIdUri))

    (items, briefItemView.pagination.getNumFound)
  }




}