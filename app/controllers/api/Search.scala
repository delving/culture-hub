package controllers.api

import controllers.DelvingController
import play.api.mvc.Action
import util.Constants
import core.search.SearchService
import collection.mutable.ListBuffer

/**
 * Search API
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Search extends DelvingController {

  def searchApi(orgId: String, provider: Option[String], dataProvider: Option[String], collection: Option[String]) = Root {
    Action {
      implicit request =>

      val hiddenQueryFilters = ListBuffer[String]()

      if(!orgId.isEmpty)
        hiddenQueryFilters += "%s:%s".format(Constants.ORG_ID, orgId)

      if(provider.isDefined) {
        hiddenQueryFilters += "%s:%s".format(Constants.PROVIDER, provider.get.replaceAll("_", " "))
      }

      if(dataProvider.isDefined) {
        hiddenQueryFilters += "%s:%s".format(Constants.OWNER, dataProvider.get.replaceAll("_", " "))
      }

      if(collection.isDefined) {
        hiddenQueryFilters += "%s:%s".format(Constants.SPEC, collection.get)
      }

      val apiResult = SearchService.getApiResult(Some(orgId), request, theme, hiddenQueryFilters.toList)

      // CORS - see http://www.w3.org/TR/cors/
      apiResult.withHeaders(
        ("Access-Control-Allow-Origin" -> "*"),
        ("Access-Control-Allow-Methods" -> "GET, POST, OPTIONS"),
        ("Access-Control-Allow-Headers" -> "X-Requested-With"),
        ("Access-Control-Max-Age" -> "86400")
      )


    }
  }

}
