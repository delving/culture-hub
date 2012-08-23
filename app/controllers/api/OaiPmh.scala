package controllers.api

import play.api.mvc.{Controller, Action}
import core.harvesting.OaiPmhService
import play.api.libs.concurrent.Promise
import controllers.DomainConfigurationAware
import play.api.Logger

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object OaiPmh extends Controller with DomainConfigurationAware {

  // TODO API documentation

  def oaipmh(orgId: String, format: Option[String], accessKey: Option[String]) = DomainConfigured {
    Action {
      implicit request =>
        Async {
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
