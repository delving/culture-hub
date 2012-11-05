package controllers.api

import play.api.mvc.{Controller, Action}
import core.harvesting.OaiPmhService
import play.api.libs.concurrent.Promise
import controllers.OrganizationConfigurationAware
import play.api.Logger
import core.HubModule
import scala.concurrent.ExecutionContext.Implicits.global

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object OaiPmh extends Controller with OrganizationConfigurationAware {

  // TODO API documentation

  def oaipmh(orgId: String, format: Option[String], accessKey: Option[String]) = OrganizationConfigured {
    Action {
      implicit request =>
        Async {
          implicit val bindingModule = HubModule
          val oaiPmhService = new OaiPmhService(request.queryString, request.uri, orgId, format, accessKey)
          Promise.pure(oaiPmhService.parseRequest).map { response =>

              if (!request.path.contains("api")) {
                Logger("CultureHub").warn("Using deprecated API call " + request.uri)
              }

              Ok(response).as(XML)
          }
        }
    }
  }

}
