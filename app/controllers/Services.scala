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

  def solrSearch : Result  = {
    import org.apache.commons.httpclient.methods.GetMethod
    import org.apache.commons.httpclient.{HttpClient, HttpMethod}
    import play.mvc.results.{RenderJson, RenderXml, RenderText, RenderBinary}
    val solrQueryString: String = request.querystring

    val solrServerUrl: String = String.format("%s/select?%s", "http://localhost:8983/solr", solrQueryString)
    val method: HttpMethod = new GetMethod(solrServerUrl)

    val httpClient: HttpClient = getHttpClient
    httpClient executeMethod (method)

    val responseContentType: String = method.getResponseHeader("Content-Type").getValue
//    method.getResponseHeaders.filter(h => h.getValue.equalsIgnoreCase("chunked")).foreach(header => response.setHeader(header.getName, header.getValue))

    val responseString: String = method.getResponseBodyAsString

    responseContentType match {
      case "application/octet-stream" => new RenderBinary(method.getResponseBodyAsStream, "solrResult", responseContentType, false)
      case x if x.startsWith("text/plain") => new RenderJson(responseString)
      case _ => new RenderXml(responseString)
    }
  }


}