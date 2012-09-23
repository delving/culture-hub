package controllers

import play.api.mvc._
import models._
import core.Constants._
import core.search._
import exceptions._
import play.api.i18n.Messages
import core.rendering.RecordRenderer
import com.mongodb.casbah.Imports._
import core.rendering.ViewType
import eu.delving.schema.SchemaVersion
import core.{SearchInService, CultureHubPlugin}
import core.indexing.IndexField

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Search extends DelvingController {

  def index(query: String, page: Int) = search(query)

  def search(query: String = "*:*") = Root {
    Action {
      implicit request =>
        try {

          val solrQuery = request.queryString.getFirst("searchIn").map { searchIn =>
            if(searchIn == "all") {
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

          val (items, briefItemView) = CommonSearch.search(Option(connectedUser), solrQuery)
          // method checks if facet is for "HasDigitalObject" - used later on (filterNot) to filter out the facet from the display list
          def isFacetHasDigitalObject(link:FacetQueryLinks) = link.facetName == IndexField.HAS_DIGITAL_OBJECT.key+"_facet"
          Ok(Template("/Search/index.html",
            'briefDocs -> items,
            'pagination -> briefItemView.getPagination,
            'facets -> briefItemView.getFacetQueryLinks.filterNot(isFacetHasDigitalObject),
            'hasDigitalObject -> briefItemView.getFacetQueryLinks.filter(isFacetHasDigitalObject),
            'themeFacets -> configuration.getFacets)).withSession(
            session +
              (RETURN_TO_RESULTS -> request.rawQueryString) +
              (SEARCH_TERM -> query))
        } catch {
          case MalformedQueryException(s, t) => BadRequest(Template("/Search/invalidQuery.html", 'query -> query))
          case c: SolrConnectionException => Error(Messages("search.backendConnectionError"))
        }
      }

    }

}
