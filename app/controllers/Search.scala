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
              List("""%s:"%s"""".format(searchIn, query))
            }
          }.getOrElse(List(query))

          renderArgs += ("breadcrumbs" -> Breadcrumbs.crumble(Map("search" -> Map("searchTerm" -> query, "returnToResults" -> request.rawQueryString))))

          val (items, briefItemView) = CommonSearch.search(Option(connectedUser), solrQuery)
          Ok(Template("/Search/index.html",
            'briefDocs -> items,
            'pagination -> briefItemView.getPagination,
            'facets -> briefItemView.getFacetQueryLinks,
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
