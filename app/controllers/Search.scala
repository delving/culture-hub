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

  def index(query: String = "*:*", page: Int = 1) = {
    if(params.allSimple().keySet().filter(key => List("query", "id", "explain").contains(key)).size == 0) {
        params.put("query", "*:*")
     }

    if (query.trim.isEmpty)
      params.put("query", "*:*")
    else
      params.put("query", query)

    // for now hardcode the facets in
    if (!params._contains("facet.field"))
      request.params.put("facet.field", Array("TYPE", "YEAR"))

    // the search service wants "start", we work with "page" in the browse mode
    request.params.put("start", page.toString)

    val chQuery = SolrQueryService.createCHQuery(request, theme, true)
    val response = CHResponse(params, theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery)
    val briefItemView = BriefItemView(response)
    Template('briefDocs -> briefItemView.getBriefDocs, 'pagination -> briefItemView.getPagination, 'facets -> briefItemView.getFacetQueryLinks)
  }

  def record(orgId: String, spec: String, recordId: String): Result = {
    val id = "%s_%s_%s".format(orgId, spec, recordId)

    val idType = DelvingIdType(id, params.all().getOrElse("idType", Array[String]("delving_chID")).head)
    val chQuery = SolrQueryService.createCHQuery(request, theme, false)
    val changedQuery = chQuery.copy(solrQuery = chQuery.solrQuery.setQuery("%s:%s".format(idType.idSearchField, idType.normalisedId)))
    val queryResponse = SolrQueryService.getSolrResponseFromServer(changedQuery.solrQuery, true)
    val response = CHResponse(params, theme, queryResponse, changedQuery)

    if(response.response.getResults.size() == 0)
      return NotFound(id)

    val fullItemView = FullItemView(SolrBindingService.getFullDoc(queryResponse), queryResponse)
    Template("/Search/object.html", 'fullDoc -> fullItemView.getFullDoc)
  }
}