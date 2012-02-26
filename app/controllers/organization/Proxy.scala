package controllers.organization

import play.api.mvc._
import controllers.DelvingController
import play.api.libs.ws.WS
import play.api.libs.concurrent.Promise

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Proxy extends DelvingController {

  val proxies = List(
    new ProxyConfiguration(
      key = "europeana",
      searchUrl = "http://api.europeana.eu/api/opensearch.rss",
      itemUrl = "http://www.europeana.eu/portal/record/",
      constantQueryString = Map("wskey" -> Seq("GJVWAUWPRZ"))) {

      override def handleSearchResponse(response: play.api.libs.ws.Response): Result = {

        val xml = response.xml

        val processed =
          <results xmlns:enrichment="http://www.europeana.eu/schemas/ese/enrichment/"
               xmlns:europeana="http://www.europeana.eu"
               xmlns:dcterms="http://purl.org/dc/terms/"
               xmlns:dc="http://purl.org/dc/elements/1.1/">{xml \\ "item"}
          </results>

        Ok(processed)
      }
    }
  )

  def list(orgId: String) = Root {
    Action {
      implicit request =>
        Ok(proxies.mkString("\n"))
    }
  }

  def query(orgId: String, proxyKey: String) = Action {
    implicit request =>
      Async {
        proxies.find(_.key == proxyKey).map {
          proxy =>
            val queryString = request.queryString.filter(e => !List("path").contains(e._1))
            val completeQueryString = (proxy.constantQueryString ++ queryString).map(entry => (entry._1, entry._2.head))

            WS.
              url(proxy.searchUrl).
              withQueryString(getWSQueryString(request, proxy.constantQueryString) : _*).
              get().map(proxy.handleSearchResponse)

        }.getOrElse {
          Promise.pure(NotFound("Proxy with key '%s' not found".format(proxyKey)))
        }

      }
  }

  def item(orgId: String, proxyKey: String, itemKey: String) = Action {
    implicit request =>
      Async {
        proxies.find(_.key == proxyKey).map {
          proxy =>

            WS.
              url(proxy.itemUrl + itemKey).
              withQueryString(getWSQueryString(request, proxy.constantQueryString) : _*).
              get().map(proxy.handleItemResponse)

        }.getOrElse {
          Promise.pure(NotFound("Proxy with key '%s' not found".format(proxyKey)))
        }

      }
  }

  private def getWSQueryString(request: RequestHeader, constantQueryString: Map[String, Seq[String]]) = {
    val queryString = request.queryString.filter(e => !List("path").contains(e._1))
    (constantQueryString ++ queryString).map(entry => (entry._1, entry._2.head)).toSeq
  }



}

case class ProxyConfiguration(key: String, searchUrl: String, itemUrl: String, constantQueryString: Map[String, Seq[String]]) {

  import play.api.mvc.Results._

  def handleSearchResponse(response: play.api.libs.ws.Response): Result = Ok(response.xml)

  def handleItemResponse(response: play.api.libs.ws.Response): Result = Ok(response.xml)

}