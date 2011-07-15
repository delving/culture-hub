package controllers

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Services extends DelvingController with HTTPClient {

  import views.User._
  import play.mvc.results.Result

  // todo change this with the real portal skins and functionality etc
  def index(user: String) = {
    val u = getUser(user)
    html.index(username = u.displayName)
  }

  def solrSearchProxy : Result  = {
    import org.apache.commons.httpclient.methods.GetMethod
    import org.apache.commons.httpclient.{HttpClient, HttpMethod}
    import play.mvc.results.{RenderJson, RenderXml, RenderBinary}
    val solrQueryString: String = request.querystring

    val solrServerUrl: String = String.format("%s/select?%s", "http://localhost:8983/solr/core0", solrQueryString)
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
    import cake.metaRepo.OaiPmhService
    import play.mvc.results.{RenderXml}
    val oaiPmhService = new OaiPmhService(request)
    new RenderXml(oaiPmhService.parseRequest)
  }

  def oaipmhSecured(accessKey: String) : Result = {
    import cake.metaRepo.OaiPmhService
    import play.mvc.results.{RenderXml}
    val oaiPmhService = new OaiPmhService(request, accessKey)
    new RenderXml(oaiPmhService.parseRequest)
  }
}