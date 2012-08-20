package core.search

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

import core.Constants
import org.apache.solr.client.solrj.response.{QueryResponse, FacetField}
import scala.collection.JavaConverters._
import exceptions.SolrConnectionException
import play.api.Logger
import play.api.mvc.RequestHeader
import core.Constants._
import collection.immutable.{List, Map}
import models.DomainConfiguration
import scala.xml.XML
import scala.xml.Elem
import org.apache.solr.client.solrj.SolrQuery
import java.net.{URLDecoder, URLEncoder}
import org.apache.commons.lang.StringEscapeUtils
import org.apache.solr.common.SolrDocumentList
import org.apache.solr.client.solrj.SolrServerException
import org.apache.solr.common.util.SimpleOrderedMap

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/28/11 10:52 AM
 */

object SolrQueryService extends SolrServer {


  val FACET_PROMPT: String = "&qf="
  val QUERY_PROMPT: String = "query="

  def renderXMLFields(field : FieldValue): (Seq[Elem], Seq[(String, String, Throwable)]) = {
    val keyAsXml = field.getKeyAsXml
    val values = field.getValueAsArray.map(value => {
      val cleanValue = if (value.startsWith("http")) value.replaceAll("&(?!amp;)", "&amp;") else StringEscapeUtils.escapeXml(value)
      try {
        Right(XML.loadString("<%s>%s</%s>\n".format(keyAsXml, cleanValue, keyAsXml)))
      } catch {
        case t: Throwable =>
          Left((cleanValue, keyAsXml, t))
      }
    })

    (values.filter(_.isRight).map(_.right.get), values.filter(_.isLeft).map(_.left.get))
  }

  def renderHighLightXMLFields(field : FieldValue) : (Seq[Elem], Seq[(String, String, Throwable)]) = {
    val values = field.getHighLightValuesAsArray.map(value =>
      try {
        Right(XML.loadString("<%s><![CDATA[%s]]></%s>\n".format(field.getKeyAsXml, value, field.getKeyAsXml)))
      } catch {
        case t: Throwable => Left(value, field.getKeyAsXml, t)
      }
    )

    (values.filter(_.isRight).map(_.right.get), values.filter(_.isLeft).map(_.left.get))
  }

  def encodeUrl(text: String): String = URLEncoder.encode(text, "utf-8")

  def decodeUrl(text: String): String = URLDecoder.decode(text, "utf-8")

  def getSolrQueryWithDefaults: SolrQuery = {

    val query = new SolrQuery("*:*")
    query set ("edismax")
    query setRows PAGE_SIZE
    query setStart 0
    query setFacet true
    query setFacetMinCount (1)
    query setFacetLimit (100)
    query setFacetMissing true
    query setFields ("*,score")
    // highlighting parameters
    query setHighlight true
    query addHighlightField ("*_snippet")
  }

  def parseSolrQueryFromParams(params: Params, configuration: DomainConfiguration) : SolrQuery = {
    import scala.collection.JavaConversions._

    val queryParams = getSolrQueryWithDefaults
    val facetsFromConfiguration: List[String] = configuration.getFacets.filterNot(_.facetName.isEmpty).map(facet => "%s_facet".format(facet.facetName))
    val facetFields: List[String] = if (params._contains("facet.field")) facetsFromConfiguration ::: params.getValues("facet.field").toList
    else facetsFromConfiguration

    params.put("facet.field", facetFields)

    def addGeoParams(hasGeoType: Boolean)  {
      if (!hasGeoType) queryParams setFilterQueries ("{!%s}".format("geofilt"))
      // set defaults
      queryParams setParam ("d", "5")
      queryParams setParam ("sfield", "location")

      params.all.filter(!_._2.isEmpty).map(params => (params._1, params._2.head)).toMap.filterKeys(key => List("geoType", "d", "sfield").contains(key)).foreach {
        item =>
          item._1 match {
            case "geoType" =>
              params.getValue("geoType") match {
                case "bbox" =>
                  queryParams setFilterQueries ("{!%s}".format("bbox"))
                case _ =>
                  queryParams setFilterQueries ("{!%s}".format("geofilt"))
              }
            case "d" =>
              queryParams setParam("d", item._2)
            case "sfield" =>
              queryParams setParam("sfield", item._2)
            case _ =>
          }
      }
    }


    params.allNonEmpty.foreach{
      entry =>
        val values = entry._2
        try {
          entry._1 match {
            case "query" =>
              queryParams setQuery (booleanOperatorsToUpperCase(values.head))
            case "start" =>
              queryParams setStart (values.head.toInt)
            case "rows" =>
              queryParams setRows (values.head.toInt)
            case "fl" | "fl[]" =>
              queryParams setFields (values.mkString(","))
            case "facet.limit" =>
              queryParams setFacetLimit (values.head.toInt)
            case "sortBy" =>
              val sortOrder = if (params.hasKeyAndValue ("sortOrder", "desc")) SolrQuery.ORDER.desc else SolrQuery.ORDER.asc
              val sortField = if (values.head.equalsIgnoreCase("random")) createRandomSortKey else values.head
              queryParams setSortField (sortField, sortOrder)
            case "facet.field" | "facet.field[]" =>
              values foreach (facet => {
                queryParams addFacetField ("{!ex=%s}%s".format(values.indexOf(facet).toString,facet))
              })
            case "group.field" =>
              // add the params stuff now
              queryParams setParam ("group", "true")
              queryParams setParam ("group.limit", "5")
              values foreach (grouping => {
                queryParams add ("group.field", grouping)
              })
            case "pt" =>
              val ptField = values.head
              if (ptField.split(",").size == 2) queryParams setParam ("pt", ptField)
              addGeoParams(params._contains("geoType"))
            case _ =>
          }
        }
        catch {
          case ex: Exception =>
            Logger("CultureHub") error ("Unable to process parameter %s with values %s".format(entry._1, values.mkString(",")), ex)
        }
    }
    queryParams
  }

  def createFilterQueryList(values: Array[String]): List[FilterQuery] = {
    if (values == null)
      List[FilterQuery]()
    else
      values.filter(_.split(":").size == 2).map(
        fq => {
          val split = fq.split(":")
          FilterQuery(split.head, split.last)
        }
      ).toList
  }

  def createCHQuery(request: RequestHeader, connectedUser: Option[String] = None, additionalSystemHQFs: List[String] = List.empty[String])(implicit configuration: DomainConfiguration): CHQuery = {
    val params = Params(request.queryString)
    createCHQuery(params, connectedUser, additionalSystemHQFs)
  }

  def createCHQuery(params: Params, connectedUser: Option[String], additionalSystemHQFs: List[String])(implicit configuration: DomainConfiguration): CHQuery = {

    def getAllFilterQueries(fqKey: String): Array[String] = {
      params.all.filter(key => key._1.equalsIgnoreCase(fqKey) || key._1.equalsIgnoreCase("%s[]".format(fqKey))).flatMap(entry => entry._2).toArray
    }

    def addPrefixedFilterQueries(fqs: List[FilterQuery], query: SolrQuery) {
      val FacetExtractor = """\{!ex=(.*)\}(.*)""".r

      val solrFacetFields = query.getFacetFields
      val facetFieldMap = if (solrFacetFields == null) Map[String,String]()
      else {
        solrFacetFields.map {
          field => field match {
            case FacetExtractor(prefix, facetName) => (facetName, prefix)
            case _ => (field, "p%i".format(solrFacetFields.indexOf(field)))
          }
        }.toMap
      }
      fqs.groupBy(_.field) foreach {
        item => {
          val prefix = facetFieldMap.get(item._1)
          val facetQueriesValues = item._2.map(_.value)
          val multiSelect = params.hasKeyAndValue("facetBoolType", "OR")
          val orString = if (!facetQueriesValues.filter(_.startsWith("[")).isEmpty)
            "%s:%s".format(item._1, facetQueriesValues.head.mkString(""))
          else if (!multiSelect)
            "%s:(%s)".format(item._1, facetQueriesValues.mkString("\"", "\" AND \"" ,"\""))
          else
            "%s:(%s)".format(item._1, facetQueriesValues.mkString("\"", "\" OR \"" ,"\""))
          prefix match {
            case Some(tag) => query addFilterQuery (if (multiSelect) "{!tag=%s}%s".format(tag, orString) else orString)
            case None => query addFilterQuery (orString)
          }
        }
      }
    }

    val format = params.getValueOrElse("format", "xml")
    val filterQueries = createFilterQueryList(getAllFilterQueries("qf"))
    val hiddenQueryFilters = createFilterQueryList(if (!configuration.searchService.hiddenQueryFilter.isEmpty) getAllFilterQueries("hqf") ++ configuration.searchService.hiddenQueryFilter.split(",") else getAllFilterQueries("hqf"))

    val query = parseSolrQueryFromParams(params, configuration)


    addPrefixedFilterQueries (filterQueries ++ hiddenQueryFilters, query)

    // manu: deactivated this query since we don't have this kind of access rights any longer
    // val defaultSystemHQFs = if (connectedUser.isEmpty) List("%s:10".format(VISIBILITY)) else List("%s:10 OR %s:\"%s\"".format(VISIBILITY, OWNER, connectedUser.get))

    val defaultSystemHQFs = List("""delving_orgId:%s""".format(configuration.orgId)) // always filter by organization

    val systemHQFs =  defaultSystemHQFs ++ additionalSystemHQFs
    systemHQFs.foreach(fq => query addFilterQuery (fq))
    CHQuery(query, format, filterQueries, hiddenQueryFilters, systemHQFs)
  }


  def getSolrItemReference(id: String, idType: DelvingIdType, findRelatedItems: Boolean)(implicit configuration: DomainConfiguration): Option[DocItemReference] = {
    val t = idType.resolve(id)
    val solrQuery = if (idType == DelvingIdType.LEGACY) {
      "%s:\"%s\" delving_orgId:%s".format(t.idSearchField, URLDecoder.decode(t.normalisedId, "utf-8"), configuration.orgId)
    } else {
      "%s:\"%s\" delving_orgId:%s".format(t.idSearchField, t.normalisedId, configuration.orgId)
    }
    val query = new SolrQuery(solrQuery)
    if (findRelatedItems) {
      val mlt = configuration.searchService.moreLikeThis
      query.set("mlt", true)
      query.set("mlt.fl", mlt.fieldList.mkString(","))
      query.set("mlt.mintf", mlt.minTermFrequency)
      query.set("mlt.mindf", mlt.minDocumentFrequency)
      query.set("mlt.minwl", mlt.minWordLength)
      query.set("mlt.maxwl", mlt.maxWordLength)
      query.set("mlt.maxqt", mlt.maxQueryTerms)
      query.set("mlt.maxntp", mlt.maxNumToken)
      query.set("mlt.qf", mlt.queryFields.map(_.replaceAll(" ", "%20")).mkString(","))
      query.set("mlt.match.include", java.lang.Boolean.TRUE)
      query.set("mlt.interestingTerms", "details")
    }
    val response = SolrQueryService.getSolrResponseFromServer(query)
    if(response.getResults.size() == 0) {
      Logger("Search").info("Didn't find record for query:  %s".format(solrQuery))
      None
    } else {
      val first = response.getResults.get(0)
      val currentFormat = if(first.containsKey(SCHEMA)) first.getFirstValue(SCHEMA).toString else ""
      val publicFormats = if(first.containsKey(ALL_SCHEMAS)) first.getFieldValues("delving_allSchemas").asScala.map(_.toString).toSeq else Seq.empty

      val relatedItems = if(findRelatedItems) {
        val moreLikeThis = response.getResponse.get("moreLikeThis").asInstanceOf[SimpleOrderedMap[Any]].asScala.head.getValue.asInstanceOf[SolrDocumentList]
         if (moreLikeThis != null && !moreLikeThis.isEmpty) {
          SolrBindingService.getBriefDocsWithIndexFromSolrDocumentList(moreLikeThis)
        } else Seq.empty
      } else {
        Seq.empty
      }

      Some(
        DocItemReference(
          first.getFirstValue(HUB_ID).toString,
          currentFormat,
          publicFormats,
          relatedItems
        )
      )
    }
  }

  def getSolrResponseFromServer(solrQuery: SolrQuery, decrementStart: Boolean = false)(implicit configuration: DomainConfiguration): QueryResponse = {

    // solr is 0 based so we need to decrement from our page start
    if (solrQuery.getStart != null && solrQuery.getStart.intValue() < 0) {
      solrQuery.setStart(0)
      Logger.warn("Solr Start cannot be negative")
    }
    if (decrementStart && solrQuery.getStart != null && solrQuery.getStart.intValue() > 0) {
      solrQuery.setStart(solrQuery.getStart.intValue() - 1)
    }
    try {
      Logger.debug(solrQuery.toString)
      runQuery(solrQuery)
    }
    catch {
      case e: SolrServerException if e.getMessage.contains("returned non ok status:400") => {
        Logger.error("Unable to fetch result", e)
        throw new MalformedQueryException("Malformed Query", e)
      }
      case e: SolrServerException if e.getMessage.contains("Server refused connection") => {
        Logger.error("Unable to connect to Solr Server", e)
        throw new SolrConnectionException("SOLR_UNREACHABLE", e)
      }
      case e: Throwable => {
        Logger.error("unable to execute SolrQuery", e)
        throw new SolrConnectionException("Malformed Query", e)
      }
    }
  }

  def createRandomNumber: Int = scala.util.Random.nextInt(1000)
  def createRandomSortKey : String = "random_%d".format(createRandomNumber)

  def createBreadCrumbList(chQuery: CHQuery) : List[BreadCrumb] = {
    import scala.collection.mutable.ListBuffer
    val solrQueryString = chQuery.solrQuery.getQuery
    val hrefBuilder = new ListBuffer[String]()
    hrefBuilder append (QUERY_PROMPT + encodeUrl(solrQueryString))
    val breadCrumbs = List[BreadCrumb](
      BreadCrumb(
        href = hrefBuilder.mkString,
        display = solrQueryString,
        value = solrQueryString
      ))
    val fqCrumbs = chQuery.filterQueries.map {
      fq =>
        hrefBuilder append (fq.toFacetString)
        BreadCrumb(
          href = hrefBuilder.mkString(FACET_PROMPT),
          display = fq.toFacetString,
          field = fq.field,
          value = fq.value
        )
    }
    if (fqCrumbs.isEmpty) {
      List(breadCrumbs.head.copy(isLast = true))
    }
    else {
      val breadCrumb = fqCrumbs.last.copy(isLast = true)
      breadCrumbs ::: fqCrumbs.init ::: List(breadCrumb)
    }
  }

  def booleanOperatorsToUpperCase(query: String): String = {
    query.split(" ").map{
      item =>
        item match {
          case "and" | "or" | "not" => item.toUpperCase
          case _ => item
        }
    }.mkString(" ")
  }

  def createPager(chResponse: CHResponse): Pager = {
    val solrStart = chResponse.chQuery.solrQuery.getStart
    Pager(
      numFound = chResponse.response.getResults.getNumFound.intValue,
      start = if (solrStart != null) solrStart.intValue() + 1 else 1,
      rows = chResponse.chQuery.solrQuery.getRows.intValue()
    )
  }

  def getMissingCount(facetField: FacetField) = {
    val last = facetField.getValues.asScala.last
    if (last.getName == null) last.getCount.toInt else 0
  }

  def createFacetQueryLinks(chResponse: CHResponse): List[FacetQueryLinks] = {
    chResponse.response.getFacetFields.asScala.map{
      facetField =>
        FacetQueryLinks(
          facetName = facetField.getName,
          links = buildFacetCountLinks(facetField, chResponse.chQuery.filterQueries),
          facetSelected = !chResponse.chQuery.filterQueries.filter(_.field.equalsIgnoreCase(facetField.getName)).isEmpty,
          missingValues = getMissingCount(facetField)
        )
    }.toList
  }

  def buildFacetCountLinks(facetField: FacetField, filterQueries: List[FilterQuery]) : List[FacetCountLink] = {
    if (facetField.getValues == null)
      List.empty
    else
      facetField.getValues.asScala.map{
        facetCount =>
          val remove = !filterQueries.filter(_.field.equalsIgnoreCase(facetField.getName)).filter(_.value.equalsIgnoreCase(facetCount.getName)).isEmpty
          FacetCountLink(
            facetCount = facetCount,
            url = makeFacetQueryUrls(facetField, filterQueries, facetCount, remove),
            remove = remove
          )
      }.toList.init /// todo maybe later replace with the more verbose filterNot(p => p.facetCount.getName == null)
  }

  def makeFacetQueryUrls(facetField: FacetField, filterQueries: List[FilterQuery], facetCount: FacetField.Count, remove: Boolean): String = {
    val facetTerms: List[String] = filterQueries.filterNot(_ == FilterQuery(facetField.getName, facetCount.getName)).map {
      fq => "%s:%s".format(fq.field, encodeUrl(fq.value))
    }
    val href = remove match {
      case true => facetTerms
      case false =>
        (facetTerms ::: List("%s:%s".format(facetCount.getFacetField.getName, facetCount.getName)))
    }
    if (!href.isEmpty) href.mkString(FACET_PROMPT,FACET_PROMPT,"") else ""
  }

}

case class DelvingIdType(idType: String, resolution: String) {

  def resolve(id: String) = new {
    lazy val idSearchField = resolution
    lazy val normalisedId = idType match {
      case DelvingIdType.PMH => id.replaceAll("/", "_")
      case _ => id
    }
  }
}

object DelvingIdType {
  val SOLR = DelvingIdType("solr", ID)
  val PMH = DelvingIdType("pmh", PMH_ID)
  val DRUPAL = DelvingIdType("drupal", "id")
  val HUB_ID = DelvingIdType("hubId", Constants.HUB_ID)
  val INDEX_ITEM = DelvingIdType("indexItem", ID)
  val LEGACY = DelvingIdType("legacy", EUROPEANA_URI)

  val types = Seq(SOLR, PMH, DRUPAL, HUB_ID, INDEX_ITEM, LEGACY)

  def apply(idType: String): DelvingIdType = types.find(_.idType == idType).getOrElse(DelvingIdType.HUB_ID)

}

case class FacetCountLink(facetCount: FacetField.Count, url: String, remove: Boolean) {

  def value = if (facetCount.getName != null) facetCount.getName else "missing"
  def count = facetCount.getCount

  override def toString: String = "<a href='%s'>%s</a> (%s)".format(url, value, if (remove) "remove" else "add")
}

case class FacetQueryLinks(facetName: String, links: List[FacetCountLink] = List.empty, facetSelected: Boolean = false, missingValues: Int = 0) {

  def getType: String = facetName
  def getLinks: List[FacetCountLink] = links
  def isFacetSelected: Boolean = facetSelected
  def getMissingValueCount: Int = missingValues

}

case class Params(queryString: Map[String, Seq[String]]) {

  private val params = collection.mutable.Map(queryString.filter(!_._2.isEmpty).map(
    k =>
      ((k._1, if (k._1.equalsIgnoreCase("query")) k._2 else k._2.map(SolrQueryService.decodeUrl(_))))
  ).toSeq: _*)

  def put(key: String, values: Seq[String]) {params put (key, values)}

  def all = params

  def allNonEmpty = params

  val allSingle = params.map(params => (params._1, params._2.head)).toMap

  def _contains(key: String) = params.contains(key)

  def valueIsNonEmpty(key: String) = _contains(key) && !getValue(key).isEmpty

  def getFirst(key: String) = getValues(key).headOption

  def getValue(key: String) = getValues(key).head

  def getValues(key: String): Seq[String] = params.get(key).getOrElse(Seq[String]())

  def hasKeyAndValue(key: String, value: String) = _contains(key) && getValue(key).equalsIgnoreCase(value)

  def getValueOrElse(key: String,  default: String): String = params.get(key).getOrElse(return default).headOption.getOrElse(default).toString

  def keys = params.keys.toList

  override def toString: String = {
    params.map(k => k._2.map(v => "%s=%s".format(k._1, v))).flatten.mkString("?", "&", "")
  }

}

case class FilterQuery(field: String, value: String) {
  def toFacetString = "%s:%s".format(field, value)
  def toPrefixedFacetString = "%s%s:%s".format(SolrQueryService.FACET_PROMPT, field, value)
  override def toString = toFacetString
}

case class SolrFacetElement(facetName: String, facetInternationalisationCode: String, nrDisplayColumns: Int = 1)

case class SolrSortElement(sortKey: String, sortOrder: SolrQuery.ORDER = SolrQuery.ORDER.asc)

case class CHQuery(solrQuery: SolrQuery, responseFormat: String = "xml", filterQueries: List[FilterQuery] = List.empty, hiddenFilterQueries: List[FilterQuery] = List.empty, systemQueries: List[String] = List.empty)

case class CHResponse(params: Params, response: QueryResponse, chQuery: CHQuery, configuration: DomainConfiguration) { // todo extend with the other response elements

  def useCacheUrl: Boolean = params.hasKeyAndValue("cache", "true")

  lazy val breadCrumbs: List[BreadCrumb] = SolrQueryService.createBreadCrumbList(chQuery)

}

/*
 * case classes converted from legacy code
 */

case class PageLink(start: Int, display: Int, isLinked: Boolean = false) {
  override def toString: String = if (isLinked) "%i:%i".format(display, start) else display.toString
}

case class BreadCrumb(href: String, display: String, field: String = "", localisedField: String = "", value: String, isLast: Boolean = false) {
  override def toString: String = "<a href=\"" + href + "\">" + display + "</a>"
}

case class Pager(numFound: Int, start: Int = 1, rows: Int = core.Constants.PAGE_SIZE) {

  private val MARGIN: Int = 5
  private val PAGE_NUMBER_THRESHOLD: Int = 7
  val hardenedRows = if (rows == 0) core.Constants.PAGE_SIZE else rows

  val totalPages = if (numFound % hardenedRows != 0) numFound / hardenedRows + 1 else numFound / hardenedRows
  val currentPageNumber = start / hardenedRows + 1
  val hasPreviousPage = start > 1
  val previousPageNumber = start - hardenedRows
  var fromPage: Int = 1
  var toPage: Int = scala.math.min(totalPages, MARGIN * 2)
  if (currentPageNumber > PAGE_NUMBER_THRESHOLD) {
    fromPage = currentPageNumber - MARGIN
    toPage = scala.math.min(currentPageNumber + MARGIN - 1, totalPages)
  }
  if (toPage - fromPage < MARGIN * 2 - 1) {
    fromPage = scala.math.max(1, toPage - MARGIN * 2 + 1)
  }
  val hasNextPage = totalPages > 1 && currentPageNumber < toPage
  val nextPageNumber = start + hardenedRows
  val pageLinks = (fromPage to toPage).map(page => PageLink(((page - 1) * hardenedRows + 1), page, currentPageNumber != page)).toList
  val lastViewableRecord = if (hasNextPage) scala.math.min(nextPageNumber, numFound) - 1 else numFound
}

case class ResultPagination (chResponse: CHResponse) {

  lazy val pager = SolrQueryService.createPager(chResponse)

  def isPrevious: Boolean = pager.hasPreviousPage

  def isNext: Boolean = pager.hasNextPage

  def getPreviousPage: Int = pager.previousPageNumber

  def getNextPage: Int = pager.nextPageNumber

  def getLastViewableRecord: Int = pager.lastViewableRecord

  def getNumFound: Int = pager.numFound

  def getRows: Int = pager.hardenedRows

  def getStart: Int = pager.start

  def getPageNumber: Int = pager.currentPageNumber

  def getPageLinks: List[PageLink] = pager.pageLinks

  def getBreadcrumbs: List[BreadCrumb] = chResponse.breadCrumbs

  def getPresentationQuery: PresentationQuery = PresentationQuery(chResponse)

  def getLastViewablePage: Int = pager.totalPages
}

case class PresentationQuery(chResponse: CHResponse) {

  val requestQueryString = chResponse.chQuery.solrQuery.getQuery

  def getUserSubmittedQuery: String = chResponse.chQuery.solrQuery.getQuery

  def getQueryForPresentation: String = createQueryForPresentation(chResponse.chQuery.solrQuery)

  def getQueryToSave: String = requestQueryString

  def getTypeQuery: String = removePresentationFilters(requestQueryString)

  def getParsedQuery: String = {
    val debug = chResponse.chQuery.solrQuery.getBool("debugQuery").booleanValue()
    if (debug)
      String.valueOf(chResponse.response.getDebugMap.get("parsedquery_toString"))
    else
      "Information not available"
  }

  private def removePresentationFilters(requestQueryString: String): String = {
    val filterQueries: Array[String] = requestQueryString.split("&")
    filterQueries.filter(fq => fq.startsWith("qf=TYPE:") || fq.startsWith("tab=") || fq.startsWith("view=") || fq.startsWith("start=")).mkString("&")
  }

  private def createQueryForPresentation(solrQuery: SolrQuery): String = {
    "query=%s%s".format(SolrQueryService.encodeUrl(solrQuery.getQuery),chResponse.chQuery.filterQueries.mkString("&qf=","&qf=", ""))
  }

}

case class BriefItemView(chResponse: CHResponse) {

  def getBriefDocs: List[BriefDocItem] = SolrBindingService.getBriefDocsWithIndex(chResponse.response, pagination.getStart)

  def getFacetQueryLinks: List[FacetQueryLinks] = SolrQueryService.createFacetQueryLinks(chResponse = chResponse)

  val pagination = ResultPagination(chResponse)

  def getPagination: ResultPagination = pagination
}

case class DocItemReference(hubId: String, defaultSchema: String, publicSchemas: Seq[String], relatedItems: Seq[BriefDocItem] = Seq.empty)

// todo implement the traits as case classes

case class MalformedQueryException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}
