package controllers.api

import play.api.mvc.Action
import core.harvesting.OaiPmhService
import play.api.libs.concurrent.Promise
import controllers.ApplicationController
import play.api.libs.concurrent.Execution.Implicits._
import com.escalatesoft.subcut.inject.BindingModule

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class OaiPmh(implicit val bindingModule: BindingModule) extends ApplicationController {

  // TODO API documentation

  def oaipmh(format: Option[String], accessKey: Option[String]) = OrganizationConfigured {
    Action {
      implicit request =>
        Async {
          val oaiPmhService = new OaiPmhService(request.queryString, request.uri, configuration.orgId, format, accessKey)
          Promise.pure(oaiPmhService.parseRequest).map { response =>
            Ok(response).as(XML)
          }
        }
    }
  }

}