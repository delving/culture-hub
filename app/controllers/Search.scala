package controllers

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 8/10/11 2:40 PM  
 */

object Search extends DelvingController {

  def index = {
    import search.{BriefItemView, CHResponse, SolrQueryService}
    val chQuery = SolrQueryService.createCHQuery(request, theme, true)
    val response = CHResponse(params, theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, theme.solrSelectUrl, true), chQuery)
    val briefItemView = BriefItemView(response)
    Template
  }
}