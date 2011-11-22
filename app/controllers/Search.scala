package controllers

import scala.collection.JavaConversions._
import search._
import models.DataSet
import play.mvc.results.Result

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 8/10/11 2:40 PM  
 */

object Search extends DelvingController {

  val ReturnToResults = "returnToResults"

  def index(query: String = "*:*", page: Int = 1) = {
    import play.mvc.Scope.Session

    val chQuery = SolrQueryService.createCHQuery(request, theme, true)
    val response = CHResponse(params, theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery)
    val briefItemView = BriefItemView(response)
    Session.current().put(ReturnToResults, request.querystring)
    Template('briefDocs -> briefItemView.getBriefDocs, 'pagination -> briefItemView.getPagination, 'facets -> briefItemView.getFacetQueryLinks)
  }

  def record(orgId: String, spec: String, recordId: String, overlay: Boolean = false): Result = {
    val id = "%s_%s_%s".format(orgId, spec, recordId)

    val idType = DelvingIdType(id, params.all().getOrElse("idType", Array[String]("hubId")).head)
    val chQuery = SolrQueryService.createCHQuery(request, theme, false)
    val changedQuery = chQuery.copy(solrQuery = chQuery.solrQuery.setQuery("%s:%s".format(idType.idSearchField, idType.normalisedId)))
    val queryResponse = SolrQueryService.getSolrResponseFromServer(changedQuery.solrQuery, true)
    val response = CHResponse(params, theme, queryResponse, changedQuery)

    if(response.response.getResults.size() == 0)
      return NotFound(id)

    val fullItemView = FullItemView(SolrBindingService.getFullDoc(queryResponse), queryResponse)
    if(overlay) {
      Template("/Search/overlay.html", 'fullDoc -> fullItemView.getFullDoc)
    }
    else {
      import play.mvc.Scope.Session
      val returnToUrl = if (Session.current().contains(ReturnToResults)) Session.current().get(ReturnToResults) else ""
      Template("/Search/object.html", 'fullDoc -> fullItemView.getFullDoc, 'returnToResults -> returnToUrl)
    }

  }
}