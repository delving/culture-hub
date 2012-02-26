package controllers.organization

import play.api.mvc._
import controllers.DelvingController
import play.api.libs.ws.WS
import play.api.libs.concurrent.Promise
import collection.immutable.Map
import xml.Elem

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Proxy extends DelvingController {

  val proxies = List[ProxyConfiguration](
    new ProxyConfiguration(
      key = "europeana",
      searchUrl = "http://api.europeana.eu/api/opensearch.rss",
      itemUrl = "http://www.europeana.eu/portal/record/",
      constantQueryString = Map("wskey" -> Seq("GJVWAUWPRZ")),
      queryRemapping = Map("query" -> "searchTerms")) {

      override def handleSearchResponse(response: play.api.libs.ws.Response): Result = {

        val xml = response.xml

        val processed: Elem =
          <results>
            <items>
              {(xml \\ "item").map(item => {
              println(item.scope.toString())
               <item>
                 <fields>
                   {item.nonEmptyChildren}
                 </fields>
               </item>
            })}
            </items>
          </results>

        Ok(processed)
      }
    }
  )

  def list(orgId: String) = Root {
    Action {
      implicit request =>
        val list =
          <explain>
            {proxies.map{proxy =>
            <item>
              <id>{proxy.key}</id>
              <url>{proxy.searchUrl}</url>
            </item>
          }}
          </explain>
        Ok(list)
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
              withQueryString(getWSQueryString(request, proxy) : _*).
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
              withQueryString(getWSQueryString(request, proxy) : _*).
              get().map(proxy.handleItemResponse)

        }.getOrElse {
          Promise.pure(NotFound("Proxy with key '%s' not found".format(proxyKey)))
        }

      }
  }

  private def getWSQueryString(request: RequestHeader, proxy: ProxyConfiguration) = {
    val queryString: Map[String, Seq[String]] = request.queryString
      .filter(e => !List("path").contains(e._1))
      .map(e => (proxy.queryRemapping.getOrElse(e._1, e._1), e._2))
    (proxy.constantQueryString ++ queryString).map(entry => (entry._1, entry._2.head)).toSeq
  }



}

case class ProxyConfiguration(
                               key: String,
                               searchUrl: String,
                               itemUrl: String,
                               constantQueryString: Map[String, Seq[String]],
                               queryRemapping: Map[String, String]) {

  import play.api.mvc.Results._

  def handleSearchResponse(response: play.api.libs.ws.Response): Result = Ok(response.xml)

  def handleItemResponse(response: play.api.libs.ws.Response): Result = Ok(response.xml)

}