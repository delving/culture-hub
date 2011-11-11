package controllers

import scala.collection.JavaConversions._
import search.{PresentationQuery, BriefItemView, CHResponse, SolrQueryService}
import views.context.PAGE_SIZE
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
      params.put("facet.field", Array("TYPE", "YEAR"))

    // the search service wants "start", we work with "page" in the browse mode
    params.put("start", page.toString)

    val chQuery = SolrQueryService.createCHQuery(request, theme, true)
    chQuery.solrQuery.setQuery()
    val response = CHResponse(params, theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery)
    val briefItemView = BriefItemView(response)
    Template('briefDocs -> briefItemView.getBriefDocs, 'pagination -> briefItemView.getPagination, 'facets -> briefItemView.getFacetQueryLinks)
  }

  def record(orgId: String, spec: String, recordId: String): Result = {
    val id = "%s:%s:%S".format(orgId, spec, recordId)
    // TODO what shall be the default
    DataSet.getRecord(id, theme.metadataPrefix.getOrElse("icn")) match {
      case None => NotFound(id)
      case Some(mdr) => Text(mdr)
    }
  }
}