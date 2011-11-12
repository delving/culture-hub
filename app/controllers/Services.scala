package controllers

import models.DataSet

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

  def searchApi : Result = {
    import search.SearchService
    SearchService.getApiResult(request, theme)
  }

  def retrieveRecord(spec: String, id: String): Result = {

    // Sjoerd: this works for e.g. http://localhost:9000/services/api/Verzetsmuseum:4e8898050364481a6dbe8dc8
    // I wrapped this into a record root element, maybe it needs to contain namespaces?

    val record = DataSet.getRecord(spec + ":" + id, theme.metadataPrefix.getOrElse("icn")).getOrElse(return NotFound)
    Xml("<record>" + record.getXmlString() + "</record>")
  }
}