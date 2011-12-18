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

import models.DataSet
import util.Constants

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Services extends DelvingController with HTTPClient {

  import play.mvc.results.Result

  // todo change this with the real portal skins and functionality etc
  def index(user: String): AnyRef = {
    val u = getUser(user) match {
      case Right(aUser) => aUser
      case Left(error) => return error
    }
    Template
  }

  def solrSearchProxy : Result  = {
    import org.apache.commons.httpclient.methods.GetMethod
    import org.apache.commons.httpclient.{HttpClient, HttpMethod}
    import play.mvc.results.{RenderJson, RenderXml, RenderBinary}
    import play.Play

    val solrQueryString: String = request.querystring

    val solrServerUrl: String = String.format("%s/select?%s", Play.configuration.getProperty("solr.baseUrl", "http://localhost:8983/solr/core2"), solrQueryString)
    val method: HttpMethod = new GetMethod(solrServerUrl)

    val httpClient: HttpClient = getHttpClient
    httpClient executeMethod (method)

    val responseContentType: String = method.getResponseHeader("Content-Type").getValue
    val solrResponseType: Option[String] = Option[String](request.params.get("wt"))

    val responseString: String = method.getResponseBodyAsString

    solrResponseType match {
      case Some("javabin") => new RenderBinary(method.getResponseBodyAsStream, "solrResult", responseContentType, false)
      case Some("json") => new RenderJson(responseString)
      case Some("xml") => new RenderXml(responseString)
      case _ => new RenderXml(responseString)
    }
  }

  def oaipmh : Result = {
    import play.mvc.results.{RenderXml}
    import cake.metaRepo.OaiPmhService

    val oaiPmhService = new OaiPmhService(request)
    new RenderXml(oaiPmhService.parseRequest)
  }

  def oaipmhSecured(accessKey: String) : Result = {
    import play.mvc.results.{RenderXml}
    import cake.metaRepo.OaiPmhService

    val oaiPmhService = new OaiPmhService(request, accessKey)
    new RenderXml(oaiPmhService.parseRequest)
  }

  def searchApi(orgId: Option[String]) : Result = {
    import search.SearchService
    orgId match {
      case Some(id) => SearchService.getApiResult(request, theme, List("%s:%s".format(Constants.ORG_ID, id)))
      case None => SearchService.getApiResult(request, theme)
    }
  }
}