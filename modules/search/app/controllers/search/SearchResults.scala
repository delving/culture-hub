package controllers.search

import controllers.{ Breadcrumbs, DelvingController }
import core.{ SearchInService, CultureHubPlugin }
import core.search.FacetQueryLinks
import core.indexing.IndexField
import core.Constants._
import core.RequestContext
import exceptions.{ SolrConnectionException, MalformedQueryException }
import play.api.i18n.Messages
import play.api.mvc._
import models.OrganizationConfiguration

/**
 * Common elements required for showing search results in the portal
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait SearchResults { this: DelvingController =>

  def search(query: String = "*:*") = Root {
    MultitenantAction {
      implicit request => searchResults(query)
    }
  }

  def searchResults(query: String, hiddenQueryFilters: Seq[String] = Seq.empty, returnToResultsBaseUrl: String = "/search")(implicit request: MultitenantRequest[AnyContent], configuration: OrganizationConfiguration) = {

    try {

      val filters = request.queryString.getFirst("searchIn").map { searchIn =>
        if (searchIn == "all") {
          List(query)
        } else {
          val searchInQuery = CultureHubPlugin.getEnabledPlugins.flatMap { p =>
            p.getServices(classOf[SearchInService]).flatMap { service =>
              service.composeSearchInQuery(searchIn, query)
            }
          }.headOption

          if (searchInQuery.isDefined) {
            List(searchInQuery.get)
          } else {
            List(query)
          }
        }
      }.getOrElse(List.empty) ++ hiddenQueryFilters

      renderArgs += ("breadcrumbs" ->
        Breadcrumbs.crumble(
          Map("search" -> Map(
            "searchTerm" -> query,
            "returnToResults" -> request.rawQueryString,
            "returnToResultsBaseUrl" -> returnToResultsBaseUrl)
          )
        )
      )

      val pluginSnippets = CultureHubPlugin.getEnabledPlugins.flatMap(_.searchResultSnippet)

      val pluginIncludes = pluginSnippets.map(_._1).toSeq

      pluginSnippets.foreach { snippet =>
        snippet._2(RequestContext(request, configuration, renderArgs(), getLang.language))
      }

      val (items, briefItemView) = searchServiceLocator.byDomain.search(Option(connectedUser), filters, request.queryString, request.host)

      def isFacetHasDigitalObject(link: FacetQueryLinks) = link.getType == IndexField.HAS_DIGITAL_OBJECT.key + "_facet"

      Ok(Template("/Search/index.html",
        'briefDocs -> items,
        'pagination -> briefItemView.getPagination,
        'facets -> briefItemView.getFacetQueryLinks.filterNot(isFacetHasDigitalObject),
        'hasDigitalObject -> briefItemView.getFacetQueryLinks.filter(isFacetHasDigitalObject),
        'themeFacets -> configuration.getFacets,
        'pluginIncludes -> pluginIncludes,
        'returnToResultsBaseUrl -> returnToResultsBaseUrl
      )
      ).withSession(
        session +
          (RETURN_TO_RESULTS -> request.rawQueryString) +
          (RETURN_TO_RESULTS_BASE_URL -> returnToResultsBaseUrl) +
          (SEARCH_TERM -> query))
    } catch {
      case MalformedQueryException(s, t) => BadRequest(Template("/Search/invalidQuery.html", 'query -> query))
      case c: SolrConnectionException => Error(Messages("search.CannotConnectToTheSearchBackend"))
    }

  }

}
