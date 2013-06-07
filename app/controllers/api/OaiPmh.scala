package controllers.api

import play.api.mvc.{ Controller, Action }
import core.harvesting.OaiPmhService
import play.api.libs.concurrent.Promise
import controllers.OrganizationConfigurationAware
import play.api.Logger
import core.HubModule
import play.api.libs.concurrent.Execution.Implicits._
import com.escalatesoft.subcut.inject.BindingModule

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class OaiPmh(implicit val bindingModule: BindingModule) extends Controller with OrganizationConfigurationAware {

  // TODO API documentation

  def oaipmh(orgId: String, format: Option[String], accessKey: Option[String]) = OrganizationConfigured {
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