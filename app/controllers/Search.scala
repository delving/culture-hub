package controllers

import play.api.mvc._
import models._
import core.Constants._
import core.search._
import exceptions._
import play.api.i18n.Messages
import core.{ RequestContext, SearchInService, CultureHubPlugin }
import core.indexing.IndexField
import com.escalatesoft.subcut.inject.BindingModule

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class Search(implicit val bindingModule: BindingModule) extends DelvingController {

  def index(query: String, page: Int) = search(query)

  def search(query: String = "*:*") = Root {
    MultitenantAction {
      implicit request =>
        try {

          val solrQuery = request.queryString.getFirst("searchIn").map { searchIn =>
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
          }.getOrElse(List(query))

          renderArgs += ("breadcrumbs" -> Breadcrumbs.crumble(Map("search" -> Map("searchTerm" -> query, "returnToResults" -> request.rawQueryString))))

          val pluginSnippets = CultureHubPlugin.getEnabledPlugins.flatMap(_.searchResultSnippet)

          val pluginIncludes = pluginSnippets.map(_._1).toSeq

          pluginSnippets.foreach { snippet =>
            snippet._2(RequestContext(request, configuration, renderArgs(), getLang.language))
          }

          val (items, briefItemView) = searchServiceLocator.byDomain.search(Option(connectedUser), solrQuery, request.queryString, request.host)
          // method checks if facet is for "HasDigitalObject" - used later on (filterNot) to filter out the facet from the display list
          def isFacetHasDigitalObject(link: FacetQueryLinks) = link.getType == IndexField.HAS_DIGITAL_OBJECT.key + "_facet"

          Ok(Template("/Search/index.html",
            'briefDocs -> items,
            'pagination -> briefItemView.getPagination,
            'facets -> briefItemView.getFacetQueryLinks.filterNot(isFacetHasDigitalObject),
            'hasDigitalObject -> briefItemView.getFacetQueryLinks.filter(isFacetHasDigitalObject),
            'themeFacets -> configuration.getFacets,
            'pluginIncludes -> pluginIncludes)
          ).withSession(
            session +
              (RETURN_TO_RESULTS -> request.rawQueryString) +
              (SEARCH_TERM -> query))
        } catch {
          case MalformedQueryException(s, t) => BadRequest(Template("/Search/invalidQuery.html", 'query -> query))
          case c: SolrConnectionException => Error(Messages("search.CannotConnectToTheSearchBackend"))
        }
    }

  }

}