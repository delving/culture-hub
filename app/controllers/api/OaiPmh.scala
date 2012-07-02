package controllers.api

import controllers.DelvingController
import play.api.mvc.Action
import core.harvesting.OaiPmhService
import play.api.libs.concurrent.Promise

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object OaiPmh extends DelvingController {

  // TODO API documentation

  def oaipmh(orgId: String, format: Option[String], accessKey: Option[String]) = Action {
    implicit request =>
      Async {
        val oaiPmhService = new OaiPmhService(request.queryString, request.uri, orgId, format, accessKey)
        Promise.pure(oaiPmhService.parseRequest).map {
          response =>

            if (!request.path.contains("api")) {
              warning("Using deprecated API call " + request.uri)
            }

            Ok(response).as(XML)
        }
      }
  }

}
