package controllers.api

import controllers.DelvingController
import play.api.mvc.Action
import core.Constants._
import core.search.SearchService
import collection.mutable.ListBuffer
import play.api.libs.concurrent.Promise

/**
 * Search API
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Search extends DelvingController {

  def searchApi(orgId: String, provider: Option[String], dataProvider: Option[String], collection: Option[String]) = Root {
    Action {
      implicit request =>
        Async {
          Promise.pure {

            if(!request.path.contains("api")) {
              warning("Using deprecated API call " + request.uri)
            }

            val hiddenQueryFilters = ListBuffer[String]("%s:%s".format(RECORD_TYPE, ITEM_TYPE_MDR))

            if (!orgId.isEmpty)
              hiddenQueryFilters += "%s:%s".format(ORG_ID, orgId)

            if (provider.isDefined) {
              hiddenQueryFilters += """%s:"%s"""".format(PROVIDER, provider.get.replaceAll("_", " "))
            }

            if (dataProvider.isDefined) {
              hiddenQueryFilters += """%s:"%s"""".format(OWNER, dataProvider.get.replaceAll("_", " "))
            }

            if (collection.isDefined) {
              hiddenQueryFilters += """%s:"%s"""".format(SPEC, collection.get)
            }

            SearchService.getApiResult(Some(orgId), request, theme, hiddenQueryFilters.toList)

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
