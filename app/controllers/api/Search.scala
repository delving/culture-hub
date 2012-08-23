package controllers.api

import play.api.mvc._
import core.Constants._
import core.search.SearchService
import collection.mutable.ListBuffer
import play.api.libs.concurrent.Promise
import controllers.DomainConfigurationAware
import play.api.Logger

/**
 * Search API
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Search extends Controller with DomainConfigurationAware {

  def searchApi(orgId: String, provider: Option[String], dataProvider: Option[String], collection: Option[String]) = DomainConfigured {
    Action {
      implicit request =>
        Async {
          Promise.pure {

            if(!request.path.contains("api")) {
              Logger("CultureHub").warn("Using deprecated API call " + request.uri)
            }

            val hiddenQueryFilters = ListBuffer[String]("%s:%s".format(RECORD_TYPE, ITEM_TYPE_MDR))

            if (!orgId.isEmpty)
              hiddenQueryFilters += "%s:%s".format(ORG_ID, orgId)

            SearchService.getApiResult(request, hiddenQueryFilters.toList)

          } map {
            // CORS - see http://www.w3.org/TR/cors/
            result => result.withHeaders(
              ("Access-Control-Allow-Origin" -> "*"),
              ("Access-Control-Allow-Methods" -> "GET, POST, OPTIONS"),
              ("Access-Control-Allow-Headers" -> "X-Requested-With"),
              ("Access-Control-Max-Age" -> "86400")

            )
          }
        }
    }
  }

}
