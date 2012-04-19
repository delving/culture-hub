/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import util.Constants
import extensions.HTTPClient
import play.api.mvc.Action
import core.search.{Params, SearchService}
import core.opendata.OaiPmhService
import play.api.libs.concurrent.Promise
import collection.mutable.ListBuffer

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Services extends DelvingController with HTTPClient {

  def solrSearchProxy = Root {
    Action {
      implicit request =>
        import org.apache.commons.httpclient.methods.GetMethod
        import org.apache.commons.httpclient.{HttpClient, HttpMethod}
        import play.api.Play
        import play.api.Play.current

        val solrQueryString: String = request.rawQueryString
        val params = Params(request.queryString)

        val solrServerUrl: String = String.format("%s/select?%s", Play.configuration.getString("solr.baseUrl").getOrElse("http://localhost:8983/solr/core2"), solrQueryString)
        val method: HttpMethod = new GetMethod(solrServerUrl)

        val httpClient: HttpClient = getHttpClient
        httpClient executeMethod (method)

        val responseContentType: String = method.getResponseHeader("Content-Type").getValue
        val solrResponseType = params.getValueOrElse("wt", "xml")

        val responseString: String = method.getResponseBodyAsString

        solrResponseType match {
          // case "javabin" => Ok(method.getResponseBodyAsStream).as(BINARY) todo enable this again , "solrResult", responseContentType, false todo test if this still works
          case "json" => Ok(responseString).as(JSON)
          case "xml" => Ok(responseString).as(XML)
          case _ => Ok(responseString).as(XML)
        }
    }
  }

  def oaipmh(orgId: String, accessKey: Option[String]) = Action {
    implicit request =>
      Async {
        val oaiPmhService = new OaiPmhService(request.queryString, request.uri, orgId, accessKey)
        Promise.pure(oaiPmhService.parseRequest).map {
          response => Ok(response).as(XML)
        }
      }
  }

  //  def oaipmhSecured(orgId: Option[String] = Some("delving"), accessKey: String)  = Root { // todo implement this properly in the routes
  //      Action {
  //        implicit request =>
  //          val oaiPmhService = new OaiPmhService(request, accessKey)
  //          Ok(oaiPmhService.parseRequest).as(XML)
  //      }
  //  }
}