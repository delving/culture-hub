package controllers

import scala.collection.JavaConversions._
import search._
import play.mvc.results.Result

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 8/10/11 2:40 PM  
 */

object Search extends DelvingController {

  val RETURN_TO_RESULTS = "returnToResults"

  def index(query: String = "*:*", page: Int = 1) = {
    val chQuery = SolrQueryService.createCHQuery(request, theme, true)
    val response = CHResponse(params, theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery)
    val briefItemView = BriefItemView(response)
    session.put(RETURN_TO_RESULTS, request.querystring)
    Template('briefDocs -> briefItemView.getBriefDocs, 'pagination -> briefItemView.getPagination, 'facets -> briefItemView.getFacetQueryLinks)
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
}