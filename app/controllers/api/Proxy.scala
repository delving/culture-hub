package controllers.api

import play.api.libs.concurrent.Promise
import collection.immutable.Map
import play.api.libs.ws.WS
import xml.{ NodeSeq, TopScope, Elem }
import play.api.mvc._
import controllers._
import play.api.{ Logger, Play }
import play.api.libs.concurrent.Execution.Implicits._
import controllers.ApiItem
import play.api.libs.ws.Response
import controllers.ApiDescription
import core.ExplainItem

/**
 * FIXME adjust namespace rendering in proxy responses. Also support JSON.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Proxy extends Controller with OrganizationConfigurationAware with RenderingExtensions {

  val proxies = List[ProxyConfiguration](europeana, wikipediaEn, wikipediaNl, wikipediaNo, wikipediaNn, amsterdamMetadataCollection)

  val log = Logger("ProxyApi")

  def explain(path: List[String]) = path match {
    case Nil =>
      Some(
        ApiDescription("The Proxy API provides a uniform API for querying accross various search APIs", List(
          ApiItem("list", "Lists all available proxies"),
          ApiItem("<proxyKey>/search", "Search via the selected proxy", "wikipedia.en/search?query=test"),
          ApiItem("<proxyKey>/item/<itemKey>", "Retrieve an item by identifier via the selected proxy", "wikipedia.en/item/bla")
        )))
    case proxyKey :: "search" :: Nil => Some(
      ApiCallDescription("Runs a search using a specific proxy", List(
        ExplainItem("query", List("a string"), "the search term")
      ))
    )
    case _ => None
  }

  def list = MultitenantAction {
    implicit request =>

      if (!request.path.contains("api")) {
        log.warn("Using deprecated API call " + request.uri)
      }

      val list =
        <explain>
          {
            proxies.map {
              proxy =>
                <item>
                  <id>{ proxy.key }</id>
                  <url>{ proxy.searchUrl }</url>
                </item>
            }
          }
        </explain>

      DOk(list, List("explain"))
  }

  def query(proxyKey: String) = Action {
    implicit request =>
      Async {

        if (!request.path.contains("api")) {
          log.warn("Using deprecated API call " + request.uri)
        }

        proxies.find(_.key == proxyKey).map {
          proxy =>
            val queryString = getWSQueryString(request, proxy)
            log.debug("Search queryString: " + queryString)

            WS.
              url(proxy.searchUrl).
              withQueryString(queryString: _*).
              get().map(r => DOk(proxy.handleSearchResponse(r), List("explain")))

        }.getOrElse {
          Promise.pure(NotFound("Proxy with key '%s' not found".format(proxyKey)))
        }

      }
  }

  def item(proxyKey: String, itemKey: String) = Action {
    implicit request =>
      Async {

        if (!request.path.contains("api")) {
          log.warn("Using deprecated API call " + request.uri)
        }

        proxies.find(_.key == proxyKey).map {
          proxy =>

            val queryString = getWSQueryString(request, proxy)
            log.debug("Item queryString " + queryString)

            WS.
              url(proxy.itemUrl + itemKey).
              withQueryString(queryString: _*).
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
    (proxy.constantQueryString ++ queryString).map(entry => (entry._1, entry._2.headOption.getOrElse(""))).toSeq
  }

  // ~~~ proxy configs

  lazy val europeana = new ProxyConfiguration(
    key = "europeana",
    searchUrl = "http://api.europeana.eu/api/opensearch.rss",
    itemUrl = "http://www.europeana.eu/portal/record/",
    constantQueryString = Map("wskey" -> Play.current.configuration.getString("cultureHub.proxy.europeana.wsKey").toSeq),
    queryRemapping = Map("query" -> "searchTerms", "start" -> "startPage"),
    paginationRemapping = Some({
      result =>
        {
          val total = (result \ "channel" \ "totalResults").text.toInt
          val start = (result \ "channel" \ "startIndex").text.toInt
          val rows = (result \ "channel" \ "itemsPerPage").text.toInt
          ProxyPager(numFound = total, start = start, rows = rows)
        }
    }),
    idExtractor = Some({
      record =>
        val EUROPEANA_URI_START = "http://www.europeana.eu/portal/record/"
        val t = (record \ "guid").text
        if (t.startsWith(EUROPEANA_URI_START)) {
          t.substring(EUROPEANA_URI_START.length, t.length - "html".length) + "srw"
        } else {
          "unknown"
        }
    })
  )

  lazy val amsterdamMetadataCollection = new ProxyConfiguration(
    key = "amdata",
    searchUrl = "http://amdata.adlibsoft.com/wwwopac.ashx",
    itemUrl = "http://amdata.adlibsoft.com/wwwopac.ashx?search=priref=",
    queryRemapping = Map("query" -> "search"),
    constantQueryString = Map("database" -> Seq("AMcollect"), "xmltype" -> Seq("grouped")),
    idExtractor = Some({
      record =>
        (record \ "@priref").text
    })
  ) {

    override def getItems(xml: Elem): NodeSeq = xml \\ "record"

    override def handleItemResponse(response: Response): NodeSeq = response.xml \\ "record"
  }

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
    val constantQueryString: Map[String, Seq[String]] = Map.empty,
    val queryRemapping: Map[String, String] = Map.empty,
    val paginationRemapping: Option[Elem => ProxyPager] = None,
    val idExtractor: Option[NodeSeq => String] = None) {

  def getItems(xml: Elem) = xml \\ "item"

  def handleSearchResponse(response: play.api.libs.ws.Response): NodeSeq = {
    val xml = response.xml

    val maybeIdExtractor = idExtractor.map(f => f(response.xml)).map { id =>
      <id>{ id }</id>
    }

    val maybePagination = paginationRemapping.map(f => f(response.xml)).map { pagination =>
      <pagination>
        <numFound>{ pagination.numFound }</numFound>
        <start>{ pagination.start }</start>
        <rows>{ pagination.rows }</rows>
      </pagination>
    }

    val processed: Elem =
      <results xmlns:atom="http://www.w3.org/2005/Atom" xmlns:enrichment="http://www.europeana.eu/schemas/ese/enrichment/" xmlns:europeana="http://www.europeana.eu" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">
        { if (maybePagination.isDefined) maybePagination.get }
        <items>
          {
            getItems(xml).map(item => {
              <item>
                {
                  if (maybeIdExtractor.isDefined) {
                    val itemId = idExtractor.map(f => f(item)).getOrElse("unknown")
                    <id>{ itemId }</id>
                  }
                }
                <fields>
                  {
                    item.nonEmptyChildren map {
                      case e: Elem => e.copy(scope = TopScope)
                      case other @ _ => other
                    }
                  }
                </fields>
              </item>
            })
          }
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

case class ProxyPager(numFound: Int, start: Int, rows: Int)