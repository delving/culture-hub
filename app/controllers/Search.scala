package controllers

import search.{BriefItemView, CHResponse, SolrQueryService}
/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 8/10/11 2:40 PM  
 */

object Search extends DelvingController {

  def index = {
  if(!params._contains("query") || !params._contains("id") || !params._contains("explain")) {
     params.put("query", "*:*")
     params.put("facet.field", Array("TYPE", "YEAR"))
   }

    val chQuery = SolrQueryService.createCHQuery(request, theme, true)
    val response = CHResponse(params, theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, theme.solrSelectUrl, true), chQuery)
    val briefItemView = BriefItemView(response)
    Template('briefDocs -> briefItemView.getBriefDocs)
  }
}