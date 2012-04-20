package controllers.api

import controllers.DelvingController
import play.api.libs.concurrent.Promise
import collection.immutable.Map
import play.api.libs.ws.{Response, WS}
import xml.{NodeSeq, TopScope, Elem}
import controllers.api.Api._
import play.api.mvc._

/**
 * FIXME adjust namespace rendering in proxy responses. Also support JSON.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Proxy extends DelvingController {

  val proxies = List[ProxyConfiguration](europeana, wikipediaEn, wikipediaNl, wikipediaNo, wikipediaNn)

  def explain(path: List[String]) = path match {
    case "proxy" :: Nil =>
      Some(
        ApiDescription("The Proxy API provides a uniform API for querying accross various search APIs", List(
          ApiItem("list", "Lists all available proxies"),
          ApiItem("<proxyKey>/search", "Search via the selected proxy", "wikipedia.en/search?query=test"),
          ApiItem("<proxyKey>/item/<itemKey>", "Retrieve an item by identifier via the selected proxy", "wikipedia.en/item/bla")
        )))
    case "proxy" :: proxyKey :: "search" :: Nil => Some(
      ApiCallDescription("Runs a search using a specific proxy", List(
          ExplainItem("query", List("a string") , "the search term")
      ))
    )
    case _ => None
  }

  def list(orgId: String) = Root {
    Action {
      implicit request =>
        val list =
          <explain>
            {proxies.map {
            proxy =>
              <item>
                <id>{proxy.key}</id>
                <url>{proxy.searchUrl}</url>
              </item>
          }}
          </explain>

        DOk(list, "item")
    }
  }

  def query(orgId: String, proxyKey: String) = Action {
    implicit request =>
      Async {
        proxies.find(_.key == proxyKey).map {
          proxy =>
            WS.
              url(proxy.searchUrl).
              withQueryString(getWSQueryString(request, proxy): _*).
              get().map(r => DOk(proxy.handleSearchResponse(r), "item"))

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
              withQueryString(getWSQueryString(request, proxy): _*).
              get().map(r => DOk(proxy.handleItemResponse(r)))

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

  lazy val wikipediaEn = new MediaWikiProxyConfiguration(
    key = "wikipedia.en",
    searchUrl = "http://en.wikipedia.org/w/api.php",
    itemUrl = "http://en.wikipedia.org/wiki/")

  lazy val wikipediaNl = new MediaWikiProxyConfiguration(
    key = "wikipedia.nl",
    searchUrl = "http://nl.wikipedia.org/w/api.php",
    itemUrl = "http://nl.wikipedia.org/wiki/")

  lazy val wikipediaNo = new MediaWikiProxyConfiguration(
    key = "wikipedia.no",
    searchUrl = "http://no.wikipedia.org/w/api.php",
    itemUrl = "http://no.wikipedia.org/wiki/")

  lazy val wikipediaNn = new MediaWikiProxyConfiguration(
    key = "wikipedia.nn",
    searchUrl = "http://nn.wikipedia.org/w/api.php",
    itemUrl = "http://nn.wikipedia.org/wiki/")

  lazy val lokalhistorieWiki = new MediaWikiProxyConfiguration(
    key = "lokalhistoriewiki.no",
    searchUrl = "http://lokalhistoriewiki.no/api.php",
    itemUrl = "http://lokalhistoriewiki.no/index.php/") {

    override def handleSearchResponse(response: Response) = {
      // the MediaWiki is an old version and the API returns nothing but JSON, and incomplete that is
      import net.liftweb.json._

      Xml.toXml(net.liftweb.json.parse(response.json.toString()))
    }
  }

}

class ProxyConfiguration(val key: String,
                         val searchUrl: String,
                         val itemUrl: String,
                         val constantQueryString: Map[String, Seq[String]],
                         val queryRemapping: Map[String, String]) {

  def getItems(xml: Elem) = xml \\ "item"

  def handleSearchResponse(response: play.api.libs.ws.Response): NodeSeq = {
    val xml = response.xml

    val processed: Elem =
      <results xmlns:atom="http://www.w3.org/2005/Atom"
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

    processed
  }

  def handleItemResponse(response: play.api.libs.ws.Response): NodeSeq = response.xml

}

/**
 * MediaWiki API
 */
case class MediaWikiProxyConfiguration(override val key: String,
                                       override val searchUrl: String,
                                       override val itemUrl: String)
  extends ProxyConfiguration(key, searchUrl, itemUrl, Map("format" -> Seq("xml"), "action" -> Seq("opensearch")), Map("query" -> "search")) {

  override def getItems(xml: Elem) = xml \\ "Item"

}