package controllers

import core.search._
import core.search.BriefItemView
import core.search.CHResponse
import core.search.Params
import play.api.mvc.RequestHeader
import models.{ Visibility, OrganizationConfiguration }

/**
 * Temporary shared controller, until search is a plugin and all dependencies match
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object CommonSearch extends DelvingController {

  def search(user: Option[String], query: List[String], params: Map[String, Seq[String]], host: String)(implicit configuration: OrganizationConfiguration) = {
    val searchContext = SearchContext(params, host, query)
    val chQuery = SolrQueryService.createCHQuery(searchContext, user)
    val queryResponse = SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true)
    val chResponse = CHResponse(queryResponse, chQuery, configuration)
    val briefItemView = BriefItemView(chResponse)

    val items: Seq[ListItem] = toListItems(briefItemView.getBriefDocs.filterNot(_.getHubId.isEmpty))(configuration)

    (items, briefItemView)
  }

  private def toListItems(briefDocs: Seq[BriefDocItem])(implicit configuration: OrganizationConfiguration) = briefDocs.map {
    bd =>
      ListItem(id = bd.getHubId,
        itemType = bd.getItemType,
        title = bd.getTitle,
        description = bd.getDescription,
        thumbnailUrl = bd.getThumbnailUri(220, configuration),
        userName = bd.getOrgId,
        isPrivate = bd.getVisibility.toInt == Visibility.PRIVATE.value,
        url = bd.getUri,
        mimeType = bd.getMimeType)
  }

}
