package controllers.organization

import play.api.mvc._
import controllers.DelvingController
import play.api.libs.ws.WS
import play.api.libs.concurrent.Promise
import collection.immutable.Map
import xml.{TopScope, Elem}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Proxy extends DelvingController {

  val proxies = List[ProxyConfiguration](europeana, wikipediaNo, wikipediaNn, lokalhistorieWiki)

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



  // ~~~ proxy configs

  lazy val europeana = new ProxyConfiguration(
        key = "europeana",
        searchUrl = "http://api.europeana.eu/api/opensearch.rss",
        itemUrl = "http://www.europeana.eu/portal/record/",
        constantQueryString = Map("wskey" -> Seq("GJVWAUWPRZ")),
        queryRemapping = Map("query" -> "searchTerms"))

  lazy val wikipediaNo = new ProxyConfiguration(
        key = "wikipedia.no",
        searchUrl = "http://no.wikipedia.org/w/api.php",
        itemUrl = "http://no.wikipedia.org/wiki/",
        constantQueryString = Map("format" -> Seq("xml"), "action" -> Seq("opensearch")),
        queryRemapping = Map("query" -> "search")) {

    override def getItems(xml: Elem) = xml \\ "Item"

  }

  lazy val wikipediaNn = new ProxyConfiguration(
        key = "wikipedia.nn",
        searchUrl = "http://nn.wikipedia.org/w/api.php",
        itemUrl = "http://nn.wikipedia.org/wiki/",
        constantQueryString = Map("format" -> Seq("xml"), "action" -> Seq("opensearch")),
        queryRemapping = Map("query" -> "search")) {

    override def getItems(xml: Elem) = xml \\ "Item"

  }

  lazy val lokalhistorieWiki = new ProxyConfiguration(
        key = "lokalhistoriewiki.no",
        searchUrl = "http://lokalhistoriewiki.no/api.php",
        itemUrl = "http://lokalhistoriewiki.no/index.php/",
        constantQueryString = Map("format" -> Seq("xml"), "action" -> Seq("opensearch")),
        queryRemapping = Map("query" -> "search")) {

      override def handleSearchResponse(response: play.api.libs.ws.Response): Result = {
        // the MediaWiki is an old version and the API returns nothing but JSON, and incomplete that is
        Ok(response.json)
      }
  }

}

case class ProxyConfiguration(
                               key: String,
                               searchUrl: String,
                               itemUrl: String,
                               constantQueryString: Map[String, Seq[String]],
                               queryRemapping: Map[String, String]) {

  import play.api.mvc.Results._

  def getItems(xml: Elem) = xml \\ "item"

  def handleSearchResponse(response: play.api.libs.ws.Response): Result = {
    val xml = response.xml

    val processed: Elem =
      <results  xmlns:atom="http://www.w3.org/2005/Atom"
                xmlns:enrichment="http://www.europeana.eu/schemas/ese/enrichment/"
                xmlns:europeana="http://www.europeana.eu"
                xmlns:dcterms="http://purl.org/dc/terms/"
                xmlns:dc="http://purl.org/dc/elements/1.1/"
                xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">
        <items>
          {getItems(xml).map(item => {
           <item>
             <fields>
               {item.nonEmptyChildren map {
               case e: Elem => e.copy(scope = TopScope)
               case other@_ => other
             }}
             </fields>
           </item>
        })}
        </items>
      </results>

    Ok(processed)
  }

  def handleItemResponse(response: play.api.libs.ws.Response): Result = Ok(response.xml)

}