package controllers.search

import play.mvc.Scope.Params
import models.PortalTheme
import controllers.SolrServer
import scala.collection.JavaConversions._
import org.apache.solr.client.solrj.{SolrResponse, SolrQuery}
import org.apache.solr.client.solrj.response.{QueryResponse, FacetField}

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/28/11 10:52 AM  
 */

object SolrQueryService extends SolrServer {

  import xml.Elem
  import play.mvc.Http.Request
  import java.net.URLEncoder

  val FACET_PROMPT: String = "&qf="
  val QUERY_PROMPT: String = "&query="

  private val MARGIN: Int = 5
  private val PAGE_NUMBER_THRESHOLD: Int = 7

  def renderXMLFields(field : FieldValue, response: CHResponse) : Seq[Elem] = {
    field.getValueAsArray.map(value =>
      try {
        import xml.XML
        XML.loadString("<%s>%s</%s>\n".format(field.getKeyAsXml, encodeUrl(value, field, response), field.getKeyAsXml))
      }
      catch {
        case ex : Exception =>
          import play.Logger
          Logger error (ex, "unable to parse " + value + "for field " + field.getKeyAsXml)
          <error/>
      }
    ).toSeq
  }

  def encode(text: String): String = URLEncoder.encode(text, "utf-8")

  def encodeUrl(field: FieldValue, request: Request, response: CHResponse): String = {
    import java.net.URLEncoder
    if (response.useCacheUrl && field.getKey == "europeana_object")
      response.theme.cacheUrl + URLEncoder.encode(field.getFirst, "utf-8")
    else field.getFirst
  }

  def encodeUrl(fields: Array[String], label: String, response: CHResponse): Array[String] = {
    if (response.useCacheUrl && label == "europeana_object")
      fields.map(fieldEntry => response.theme.cacheUrl + URLEncoder.encode(fieldEntry, "utf-8"))
    else fields
  }

  def encodeUrl(content: String, field: FieldValue, response: CHResponse): String = {
    import java.net.URLEncoder
    if (response.useCacheUrl && field.getKey == "europeana_object")
      response.theme.cacheUrl + URLEncoder.encode(content, "utf-8")
    else if (content.startsWith("http://")) content.replaceAll("&", "&amp;")
    else content.replaceAll(" & ", "&amp;")
  }

  import org.apache.solr.client.solrj.SolrQuery
  import play.mvc.Http.Request
  import models.{PortalTheme, FacetElement}

  def getSolrQueryWithDefaults(facets: List[FacetElement] = List.empty): SolrQuery = {

    val query = new SolrQuery("*:*")
    query set ("edismax")
    query setRows 12
    query setStart 0
    query setFacet true
    query setFacetLimit (1)
    query setFacetLimit (100)
    facets foreach (facet => query setFacetPrefix (facet.facetPrefix, facet.facetName))
    query setFields ("*,score")
    query
  }

  def getSolrFullItemQueryWithDefaults(facets: List[FacetElement] = List.empty): SolrQuery = {
    // todo finish this
    val query = new SolrQuery("*:*")
    query set ("edismax")
    query setRows 12
    query setStart 0
    query
  }

  def parseRequest(request: Request, theme: PortalTheme) : SolrQuery = {
    import scala.collection.JavaConversions._

    val query = getSolrQueryWithDefaults(theme.facets)

    val params = request.params
//    require(params._contains("query") && !params.get("query").isEmpty)

    params.all().foreach{
      key =>
        val values = key._2
        key._1 match {
          case "query" =>
            query setQuery (values.head)
          case "start" =>
            if (!values.isEmpty) query setStart (values.head.toInt)
          case "rows" =>
            if (!values.isEmpty) query setRows (values.head.toInt)
          case "qf" =>
          case "hqf" =>
          case "fl" =>
          case "facet.limit" =>
          case _ =>
        }
    }
    query
  }

  def createCHQuery(request: Request, theme: PortalTheme, summaryView: Boolean = true): CHQuery = {
    val format = if (request.params._contains("format") && !request.params.get("format").isEmpty) request.params.get("format").trim() else "xml"
    val query = parseRequest(request, theme)
    CHQuery(query, format)
  }

  //todo implement this
//  def getFullItemView(chQuery: CHQuery): FullItemView

  private def getSolrResponseFromServer(solrQuery: SolrQuery, solrSelectUrl: String, decrementStart: Boolean = false): QueryResponse = {
    import org.apache.solr.common.SolrException
    import play.Logger
    import org.apache.solr.client.solrj.SolrServerException

    // solr is 0 based so we need to decrement from our page start
    if (solrQuery.getStart != null && solrQuery.getStart.intValue() < 0) {
      solrQuery.setStart(0)
      Logger.warn("Solr Start cannot be negative")
    }
    if (decrementStart && solrQuery.getStart != null && solrQuery.getStart.intValue() > 0) {
      solrQuery.setStart(solrQuery.getStart.intValue() - 1)
    }
    try {
      runQuery(solrQuery, solrSelectUrl)
    }
    catch {
      case e: SolrException => {
        Logger.error("unable to execute SolrQuery", e)
        throw new MalformedQueryException("Malformed Query", e)
      }
      case e: SolrServerException if e.getMessage.equalsIgnoreCase("Error executing query") => {
        Logger.error("Unable to fetch result", e)
        throw new MalformedQueryException("Malformed Query", e)
      }
      case e: SolrServerException => {
        import models.SolrConnectionException
        Logger.error("Unable to connect to Solr Server", e)
        throw new SolrConnectionException("SOLR_UNREACHABLE", e)
      }
    }
  }

  def createRandomNumber: Int = scala.util.Random.nextInt(1000)
  def createRandomSortKey : String = "random_%i".format(createRandomNumber)

}
case class FilterQuery(field: String, value: String)

case class CHQuery(solrQuery: SolrQuery, responseFormat: String = "xml", filterQueries: List[FilterQuery] = List.empty, hiddenFilterQueries: List[FilterQuery] = List.empty) {

}

case class CHResponse(breadCrumbs: List[BreadCrumb] = List.empty, params: Params, theme: PortalTheme, response: QueryResponse, chQuery: CHQuery) { // todo extend with the other response elements

  def useCacheUrl: Boolean = if (params._contains("cache") && params.get("cache").equalsIgnoreCase("true")) true else false

}

/*
 * case classes converted from legacy code
 */

case class PageLink(start: Int, display: Int, isLinked: Boolean = false) {
  override def toString: String = if (isLinked) "%i:%i".format(display, start) else display.toString
}

case class BreadCrumb(href: String, display: String, field: String, localisedField: String, value: String, isLast: Boolean) {
  override def toString: String = "<a href=\"" + href + "\">" + display + "</a>"
}

case class FacetCountLink(facetCount: FacetField.Count, url: String, remove: Boolean) {

  def value = facetCount.getName
  def count = facetCount.getCount

  override def toString: String = "<a href='%s'>%s</a> (%s)".format(url, value, if (remove) "remove" else "add")
}

case class FacetQueryLinks(facetType: String, links: List[FacetCountLink] = List.empty, facetSelected: Boolean = false)


trait ResultPagination {

  def isPrevious: Boolean

  def isNext: Boolean

  def getPreviousPage: Int

  def getNextPage: Int

  def getLastViewableRecord: Int

  def getNumFound: Int

  def getRows: Int

  def getStart: Int

  def getPageLinks: List[PageLink]

  def getBreadcrumbs: List[BreadCrumb]

  def getPresentationQuery: PresentationQuery

  def getPageNumber: Int
}

// implemented
case class PresentationQuery(chResponse: CHResponse, requestQueryString: String) {

  def getUserSubmittedQuery: String = chResponse.chQuery.solrQuery.getQuery

  def getQueryForPresentation: String = createQueryForPresentation(chResponse.chQuery.solrQuery)

  def getQueryToSave: String = requestQueryString

  def getTypeQuery: String = removePresentationFilters(requestQueryString)

  def getParsedQuery: String = {
    val debug = chResponse.chQuery.solrQuery.getBool("debugQuery").booleanValue()
    if (debug != null && debug)
      String.valueOf(chResponse.response.getDebugMap.get("parsedquery_toString"))
    else
      "Information not available"
  }

  private def removePresentationFilters(requestQueryString: String): String = {
    var filterQueries: Array[String] = requestQueryString.split("&")
    filterQueries.filter(fq => fq.startsWith("qf=TYPE:") || fq.startsWith("tab=") || fq.startsWith("view=") || fq.startsWith("start=")).mkString("&")
  }

  private def createQueryForPresentation(solrQuery: SolrQuery): String = {
    "query=%s%s".format(SolrQueryService.encode(solrQuery.getQuery),chResponse.chQuery.filterQueries.mkString("&qf=", "&qf=", ""))
  }

}

/**
 * todo: javadoc
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Gerald de Jong <geralddejong@gmail.com>
 */
case class BriefItemView(response: QueryResponse, chQuery: CHQuery) {

  import java.util.List

  def getBriefDocs: List[BriefDocItem] = SolrBindingService.getBriefDocs(response)

//  def getFacetQueryLinks: List[FacetQueryLinks]
//
//  def getPagination: ResultPagination
//
//  def getFacetLogs: Map[String, String]
//
//  def getMatchDoc: BriefDocItem
//
//  def getSpellCheck: SpellCheckResponse
//
//  def getFacetMap: FacetMap
}

case class FullItemView(fullItem: FullDocItem, response: QueryResponse) {
//case class FullItemView(pager: DocIdWindowPager, relatedItems: List[BriefDocItem], fullItem: FullDocItem) {

//  def getDocIdWindowPager: DocIdWindowPager = pager
//
//  def getRelatedItems: List[BriefDocItem] = relatedItems

  def getFullDoc: FullDocItem = fullItem
}

trait DocIdWindowPager {


  def getDocIdWindow: DocIdWindow

  def isNext: Boolean

  def isPrevious: Boolean

  def getQueryStringForPaging: String

  def getFullDocUri: String

  def getNextFullDocUrl: String

  def getPreviousFullDocUrl: String

  def getNextUri: String

  def getNextInt: Int

  def getPreviousUri: String

  def getPreviousInt: Int

  def getQuery: String

  def getReturnToResults: String

  def getPageId: String

  def getTab: String

  override def toString: String

  def getStartPage: String

  def getBreadcrumbs: List[BreadCrumb]

  def getNumFound: Int

  def getFullDocUriInt: Int

  def setPortalName(portalName: String): Unit

//  def initialize(httpParameters: Map[String, Array[String]], breadcrumbFactory: BreadcrumbFactory, locale: Locale, originalBriefSolrQuery: SolrQuery, queryModelFactory: QueryModelFactory, metadataModel: RecordDefinition): Unit

  def getSortBy: String
}

/**
 * @author Gerald de Jong <geralddejong@gmail.com>
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 */
trait DocIdWindow extends PagingWindow {
  def getIds: List[_ <: DocId]
}

/**
 * @author Gerald de Jong <geralddejong@gmail.com>
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 */
trait PagingWindow {
  def getOffset: Integer

  def getHitCount: Integer
}

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since Feb 20, 2010 8:40:07 PM
 */
trait DocId {
  def getEuropeanaUri: String
}

class MalformedQueryException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}