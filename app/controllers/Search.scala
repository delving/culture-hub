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
    // always give back the recordType facet
    request.params.put("facet.field", "delving_recordType")

    val chQuery = SolrQueryService.createCHQuery(request, theme, true)
    val response = CHResponse(params, theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery)
    val briefItemView = BriefItemView(response)
    Template('briefDocs -> briefItemView.getBriefDocs, 'pagination -> briefItemView.getPagination, 'facets -> briefItemView.getFacetQueryLinks)
  }

  def record(orgId: String, spec: String, recordId: String): Result = {
    val id = "%s_%s_%s".format(orgId, spec, recordId)

    val idType = DelvingIdType(id, params.all().getOrElse("idType", Array[String]("hubId")).head)
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