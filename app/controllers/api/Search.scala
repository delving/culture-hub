package controllers.api

import play.api.mvc._
import core.Constants._
import core.indexing.IndexField._
import core.search.SearchService
import play.api.libs.concurrent.Promise
import controllers.{BoundController, OrganizationConfigurationAware}
import play.api.Logger
import core.{OrganizationCollectionLookupService, HubModule}
import play.api.cache.Cache
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Search API
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Search extends BoundController(HubModule) with Search

trait Search extends Controller with OrganizationConfigurationAware { this: Controller with BoundController with OrganizationConfigurationAware =>

  val organizationCollectionLookupService = inject [ OrganizationCollectionLookupService ]

  def searchApi(orgId: String, provider: Option[String], dataProvider: Option[String], collection: Option[String]) = OrganizationConfigured {
    Action {
      implicit request =>
        Async {
          Promise.pure {

            if(!request.path.contains("api")) {
              Logger("CultureHub").warn("Using deprecated API call " + request.uri)
            }

            val itemTypes = Cache.getOrElse("itemTypes", 300) {
              organizationCollectionLookupService.findAll.map(_.itemType).distinct
            }

            val hiddenQueryFilters = List(
              "(%s)".format(
                itemTypes.map(t =>
                 "%s:%s".format(RECORD_TYPE.key, t.itemType)
                ).mkString(" OR ")
              ),
              "%s:%s".format(ORG_ID.key, configuration.orgId)
            )

            SearchService.getApiResult(request, hiddenQueryFilters)

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
