package controllers.api

import controllers.DelvingController
import play.api.mvc.Action
import core.opendata.OaiPmhService
import play.api.libs.concurrent.Promise

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object OpenData extends DelvingController {

  def oaipmh(orgId: String, accessKey: Option[String]) = Action {
    implicit request =>
      Async {
        val oaiPmhService = new OaiPmhService(request.queryString, request.uri, orgId, accessKey)
        Promise.pure(oaiPmhService.parseRequest).map {
          response => Ok(response).as(XML)
        }
      }
  }

  def explain(path: List[String]) = path match {
    case _ =>
      Some(
        ApiCallDescription("Implementation of the OAI-PMH protocol. See http://www.openarchives.org/pmh/ for more information", List(
          ExplainItem("verb", List("GetRecord", "Identify", "ListIdentifiers", "ListMetadataFormats", "ListRecords", "ListSets"))
        )))
  }



}
