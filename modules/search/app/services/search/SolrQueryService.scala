package services.search

import core.indexing.IndexField
import org.apache.solr.client.solrj.response.{ QueryResponse, FacetField }
import scala.collection.JavaConverters._
import exceptions.{ MalformedQueryException, SolrConnectionException }
import play.api.Logger
import core.indexing.IndexField._
import collection.immutable.{ List, Map }
import models.{ MetadataAccessors, OrganizationConfiguration }
import scala.xml.XML
import scala.xml.Elem
import org.apache.solr.client.solrj.SolrQuery
import java.net.{ URLDecoder, URLEncoder }
import org.apache.commons.lang.StringEscapeUtils
import org.apache.solr.common.SolrDocumentList
import org.apache.solr.client.solrj.SolrServerException
import org.apache.solr.common.util.SimpleOrderedMap
import core.indexing.IndexField._
import core.Constants._
import core.search._
import scala.collection.mutable.ArrayBuffer

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/28/11 10:52 AM
 */

object SolrQueryService extends SolrServer {

  val log = Logger("CultureHub")

  val FACET_PROMPT: String = "&qf="
  val QUERY_PROMPT: String = "query="

  def renderXMLFields(field: FieldValue, context: SearchContext): (Seq[Elem], Seq[(String, String, Throwable)]) = {
    val keyAsXml = field.getKeyAsXml
    val values = field.getValueAsArray.map(value => {
      val withCacheUrl = prependImageCacheUrl(field.getKeyAsXml, value, context)
      val cleanValue = escapeValue(withCacheUrl)
      try {
        Right(XML.loadString("<%s>%s</%s>\n".format(keyAsXml, cleanValue, keyAsXml)))
      } catch {
        case t: Throwable =>
          Left((cleanValue, keyAsXml, t))
      }
    })

    (values.filter(_.isRight).map(_.right.get), values.filter(_.isLeft).map(_.left.get))
  }

  def renderKMLSimpleDataFields(field: FieldValue, simpleData: Boolean, context: SearchContext): (Seq[Elem], Seq[(String, String, Throwable)]) = {
    val keyAsXml = field.getKeyAsXml
    val values = field.getValueAsArray.map(value => {
      val withCacheUrl = prependImageCacheUrl(field.getKeyAsXml, value, context)
      val cleanValue = escapeValue(withCacheUrl)
      try {
        if (simpleData)
          Right(XML.loadString("<SimpleData name='%s'>%s</SimpleData>\n".format(field.getKey, cleanValue)))
        else
          Right(XML.loadString("<Data name='%s'><value>%s</value></Data>\n".format(field.getKeyAsXml, cleanValue)))
      } catch {
        case t: Throwable =>
          Left((cleanValue, keyAsXml, t))
      }
    })

    (values.filter(_.isRight).map(_.right.get), values.filter(_.isLeft).map(_.left.get))
  }

  def renderHighLightXMLFields(field: FieldValue): (Seq[Elem], Seq[(String, String, Throwable)]) = {
    val values = field.getHighLightValuesAsArray.map(value =>
      try {
        Right(XML.loadString("<%s><![CDATA[%s]]></%s>\n".format(field.getKeyAsXml, value, field.getKeyAsXml)))
      } catch {
        case t: Throwable => Left(value, field.getKeyAsXml, t)
      }
    )

    (values.filter(_.isRight).map(_.right.get), values.filter(_.isLeft).map(_.left.get))
  }

  def escapeValue(value: String) = if (value.startsWith("http")) value.replaceAll("&(?!amp;)", "&amp;") else StringEscapeUtils.escapeXml(value)

  private val imageUrlFields = Seq("delving:imageUrl", "europeana:isShownBy", "europeana:object")
  private val thumbnailUrlFields = Seq("delving:thumbnail")

  def prependImageCacheUrl(xmlKey: String, value: String, context: SearchContext) = {
    if (context.configuration.objectService.imageCacheEnabled) {
      def url(imageType: String) = "http://%s/%s/cache?id=%s".format(context.host, imageType, URLEncoder.encode(value, "utf-8"))
      if (imageUrlFields.contains(xmlKey)) {
        url("image")
      } else if (thumbnailUrlFields.contains(xmlKey)) {
        url("thumbnail")
      } else {
        value
      }
    } else {
      value
    }
  }

  def encodeUrl(text: String): String = URLEncoder.encode(text, "utf-8")

  def decodeUrl(text: String): String = URLDecoder.decode(text, "utf-8")

  def getSolrQueryWithDefaults(implicit configuration: OrganizationConfiguration): SolrQuery = {

    val query = new SolrQuery("*:*")
    query set ("edismax")
    query setRows configuration.searchService.pageSize
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

  def parseSolrQueryFromParams(params: Params)(implicit configuration: OrganizationConfiguration): SolrQuery = {
    import scala.collection.JavaConversions._

    val queryParams = getSolrQueryWithDefaults
    val facetsFromConfiguration: List[String] = configuration.getFacets.filterNot(_.facetName.isEmpty).map(facet => "%s_facet".format(facet.facetName))
    val facetFields: List[String] = if (params._contains("facet.field")) facetsFromConfiguration ::: params.getValues("facet.field").toList
    else facetsFromConfiguration

    params.put("facet.field", facetFields)

    def addGeoParams(hasGeoType: Boolean) {
      // set defaults
      val sfield: String = if (params.allNonEmpty.getOrElse("sortBy", List("empty").toBuffer).head.toString.startsWith("geodist")) GEOHASH_MONO.key else GEOHASH.key
      if (!hasGeoType) queryParams setFilterQueries ("{!%s}".format("geofilt"))

      queryParams setParam ("d", "5")
      queryParams setParam ("sfield", sfield)

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
              queryParams setParam ("d", item._2)
            case "sfield" =>
              queryParams setParam ("sfield", item._2)
            case _ =>
          }
      }
    }

    params.allNonEmpty.foreach {
      entry =>
        val values = entry._2
        try {
          entry._1 match {
            case "query" =>
              queryParams setQuery (booleanOperatorsToUpperCase(values.head))
            case "start" =>
              queryParams setStart (values.head.toInt)
            case "rows" =>
              val queryRows: Int = values.head.toInt
              queryParams setRows (if (queryRows > configuration.searchService.rowLimit) configuration.searchService.rowLimit else queryRows)
            case "fl" | "fl[]" =>
              queryParams setFields (values.mkString(","))
            case "facet.limit" =>
              queryParams setFacetLimit (values.head.toInt)
            case "sortBy" =>
              val sortOrder = if (params.hasKeyAndValue("sortOrder", "desc")) SolrQuery.ORDER.desc else SolrQuery.ORDER.asc
              val sortField = if (values.head.equalsIgnoreCase("random")) createRandomSortKey
              else if (values.head.equalsIgnoreCase("geodist")) "geodist()"
              else values.head
              queryParams setSortField (sortField, sortOrder)
            case "facet.field" | "facet.field[]" =>
              values foreach (facet => {
                queryParams addFacetField ("{!ex=%s}%s".format(values.indexOf(facet).toString, facet))
              })
            case "group.field" =>
              // add the params stuff now
              queryParams setParam ("group", "true")
              queryParams setParam ("group.limit", "5")
              queryParams setParam ("group.ngroups", "true")
              values foreach (grouping => {
                queryParams add ("group.field", grouping)
              })
            case "pt" =>
              val ptField = values.head
              if (ptField.split(",").size == 2) queryParams setParam ("pt", ptField)
              addGeoParams(params._contains("geoType"))
            case _ =>
          }
        } catch {
          case ex: Exception =>
            log.error("Unable to process parameter %s with values %s".format(entry._1, values.mkString(",")), ex)
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

  def createCHQuery(context: SearchContext, connectedUser: Option[String] = None)(implicit configuration: OrganizationConfiguration): CHQuery = {
    createCHQuery(context.params, connectedUser, context.hiddenQueryFilters)
  }

  def createCHQuery(params: Params, connectedUser: Option[String], additionalSystemHQFs: Seq[String])(implicit configuration: OrganizationConfiguration): CHQuery = {

    def getAllFilterQueries(fqKey: String): Array[String] = {
      params.all.filter(key => key._1.equalsIgnoreCase(fqKey) || key._1.equalsIgnoreCase("%s[]".format(fqKey))).flatMap(entry => entry._2).toArray
    }

    def addPrefixedFilterQueries(fqs: List[FilterQuery], query: SolrQuery) {
      val FacetExtractor = """\{!ex=(.*)\}(.*)""".r

      val solrFacetFields = query.getFacetFields
      val facetFieldMap = if (solrFacetFields == null) Map[String, String]()
      else {
        solrFacetFields.map {
          field =>
            field match {
              case FacetExtractor(prefix, facetName) => (facetName, prefix)
              case _ => (field, s"p${solrFacetFields.indexOf(field)}")
            }
        }.toMap
      }
      fqs.groupBy(_.field) foreach {
        item =>
          {
            val prefix = facetFieldMap.get(item._1)
            val facetQueriesValues = item._2.map(_.value)
            val multiSelect = params.hasKeyAndValue("facetBoolType", "OR")
            val orString = if (!facetQueriesValues.filter(_.startsWith("[")).isEmpty)
              "%s:%s".format(item._1, facetQueriesValues.head.mkString(""))
            else if (!multiSelect)
              "%s:(%s)".format(item._1, facetQueriesValues.mkString("\"", "\" AND \"", "\""))
            else
              "%s:(%s)".format(item._1, facetQueriesValues.mkString("\"", "\" OR \"", "\""))
            prefix match {
              case Some(tag) => query addFilterQuery (if (multiSelect) "{!tag=%s}%s".format(tag, orString) else orString)
              case None => query addFilterQuery (orString)
            }
          }
      }
    }

    val format = params.getValueOrElse("format", "xml")
    // todo enable later again when the fieldmarkers for GEOHASH is working correctly
    if (format.startsWith("kml")) {
      params.put("hqf", List("%s:true".format(HAS_GEO_HASH.key)) ++ params.getValues("hqf"))
    }
    val filterQueries = createFilterQueryList(getAllFilterQueries("qf"))
    val hiddenQueryFilters = createFilterQueryList(if (!configuration.searchService.hiddenQueryFilter.isEmpty) getAllFilterQueries("hqf") ++ configuration.searchService.hiddenQueryFilter.split(",") else getAllFilterQueries("hqf"))

    val query = parseSolrQueryFromParams(params)

    addPrefixedFilterQueries(filterQueries ++ hiddenQueryFilters, query)

    val defaultSystemHQFs = List("""delving_orgId:%s""".format(configuration.orgId)) // always filter by organization

    val systemHQFs = defaultSystemHQFs ++ additionalSystemHQFs
    systemHQFs.foreach(fq => query addFilterQuery (fq))
    CHQuery(query, format, filterQueries, hiddenQueryFilters, systemHQFs, params.getFirst("group.field"))
  }

  def getSolrItemReference(id: String, idType: DelvingIdType, findRelatedItems: Boolean, relatedItemsCount: Int)(implicit configuration: OrganizationConfiguration): Option[DocItemReference] = {
    val t = idType.resolve(id)
    val solrQuery = if (idType == DelvingIdType.LEGACY) {
      "%s:\"%s\" delving_orgId:%s".format(t.idSearchField, URLDecoder.decode(t.normalisedId, "utf-8"), configuration.orgId)
    } else if (idType == DelvingIdType.FREE || idType == DelvingIdType.ITIN) {
      "%s delving_orgId:%s".format(URLDecoder.decode(t.normalisedId, "utf-8"), configuration.orgId)
    } else {
      "%s:\"%s\" delving_orgId:%s".format(t.idSearchField, t.normalisedId, configuration.orgId)
    }
    val query = new SolrQuery(solrQuery)
    if (findRelatedItems) {
      val mlt = configuration.searchService.moreLikeThis
      query.addFilterQuery(s"${IndexField.ORG_ID.key}:${configuration.orgId}")
      query.set("mlt", true)
      query.set("mlt.count", relatedItemsCount)
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
    if (response.getResults.size() == 0) {
      None
    } else {
      val first = response.getResults.get(0)
      val currentFormat = if (first.containsKey(SCHEMA.key)) first.getFirstValue(SCHEMA.key).toString else ""
      val publicFormats = if (first.containsKey(ALL_SCHEMAS.key)) first.getFieldValues(ALL_SCHEMAS.key).asScala.map(_.toString).toSeq else Seq.empty

      val relatedItems = if (findRelatedItems) {
        val moreLikeThis = response.getResponse.get("moreLikeThis").asInstanceOf[SimpleOrderedMap[Any]].asScala.head.getValue.asInstanceOf[SolrDocumentList]
        if (moreLikeThis != null && !moreLikeThis.isEmpty) {
          SolrBindingService.getBriefDocs(moreLikeThis)
        } else Seq.empty
      } else {
        Seq.empty
      }

      Some(
        DocItemReference(
          Option(first.getFirstValue(HUB_ID.key)).map(_.toString).getOrElse(""),
          currentFormat,
          publicFormats,
          relatedItems,
          SolrBindingService.getBriefDocs(response).headOption
        )
      )
    }
  }

  def getSolrResponseFromServer(solrQuery: SolrQuery, decrementStart: Boolean = false)(implicit configuration: OrganizationConfiguration): QueryResponse = {

    // solr is 0 based so we need to decrement from our page start
    if (solrQuery.getStart != null && solrQuery.getStart.intValue() < 0) {
      solrQuery.setStart(0)
      log.warn("Solr Start cannot be negative")
    }
    if (decrementStart && solrQuery.getStart != null && solrQuery.getStart.intValue() > 0) {
      solrQuery.setStart(solrQuery.getStart.intValue() - 1)
    }
    try {
      log.debug(solrQuery.toString)
      runQuery(solrQuery)
    } catch {
      case e: SolrServerException if e.getMessage.contains("returned non ok status:400") => {
        log.error("Unable to fetch result", e)
        throw new MalformedQueryException("Malformed Query", e)
      }
      case e: SolrServerException if e.getMessage.contains("Server refused connection") => {
        log.error("Unable to connect to Solr Server", e)
        throw new SolrConnectionException("SOLR_UNREACHABLE", e)
      }
      case e: SolrServerException if e.getMessage.contains("Timeout occured while waiting response from server") => {
        log.error("Timeout while waiting for SOLR server to respond")
        throw new SolrConnectionException("SOLR connection timeout", e)
      }
      case e: Throwable => {
        log.error("unable to execute SolrQuery", e)
        throw new SolrConnectionException("Unknown SOLR error", e)
      }
    }
  }

  def createRandomNumber: Int = scala.util.Random.nextInt(1000)
  def createRandomSortKey: String = "random_%d".format(createRandomNumber)

  def createBreadCrumbList(chQuery: CHQuery): List[BreadCrumb] = {
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
    } else {
      val breadCrumb = fqCrumbs.last.copy(isLast = true)
      breadCrumbs ::: fqCrumbs.init ::: List(breadCrumb)
    }
  }

  def booleanOperatorsToUpperCase(query: String): String = {
    query.split(" ").map {
      item =>
        item match {
          case "and" | "or" | "not" => item.toUpperCase
          case _ => item
        }
    }.mkString(" ")
  }

  def createPager(chResponse: CHResponse)(implicit configuration: OrganizationConfiguration): Pager = {
    val solrStart = chResponse.chQuery.solrQuery.getStart
    val numFound = if (chResponse.chQuery.groupField.isDefined) {
      // for grouped responses, we don't use the number of total matches, but the number of returned documents for all groups instead
      val groupResponses = chResponse.response.getGroupResponse.getValues
      groupResponses.asScala.flatMap(_.getValues.asScala).foldLeft(0)((sum, group) => sum + group.getResult.size())
    } else {
      chResponse.response.getResults.getNumFound.intValue
    }
    Pager(
      numFound = numFound,
      start = if (solrStart != null) solrStart.intValue() + 1 else 1,
      rows = chResponse.chQuery.solrQuery.getRows.intValue(),
      pageSize = configuration.searchService.pageSize
    )
  }

  def getMissingCount(facetField: FacetField) = {
    val last = facetField.getValues.asScala.last
    if (last.getName == null) last.getCount.toInt else 0
  }

  def createFacetQueryLinks(chResponse: CHResponse): List[SOLRFacetQueryLinks] = {
    chResponse.response.getFacetFields.asScala.map {
      facetField =>
        SOLRFacetQueryLinks(
          facetName = facetField.getName,
          links = buildFacetCountLinks(facetField, chResponse.chQuery.filterQueries),
          facetSelected = !chResponse.chQuery.filterQueries.filter(_.field.equalsIgnoreCase(facetField.getName)).isEmpty,
          missingValues = getMissingCount(facetField)
        )
    }.toList
  }

  def buildFacetCountLinks(facetField: FacetField, filterQueries: List[FilterQuery]): List[SOLRFacetCountLink] = {
    if (facetField.getValues == null)
      List.empty
    else
      facetField.getValues.asScala.map {
        facetCount =>
          val remove = !filterQueries.filter(_.field.equalsIgnoreCase(facetField.getName)).filter(_.value.equalsIgnoreCase(facetCount.getName)).isEmpty
          SOLRFacetCountLink(
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
        (facetTerms ::: List("%s:%s".format(facetCount.getFacetField.getName, Option(facetCount.getName).map(encodeUrl(_)).getOrElse(""))))
    }
    if (!href.isEmpty) href.mkString(FACET_PROMPT, FACET_PROMPT, "") else ""
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
  val SOLR = DelvingIdType("solr", ID.key)
  val PMH = DelvingIdType("pmh", PMH_ID.key)
  val DRUPAL = DelvingIdType("drupal", "id")
  val HUB = DelvingIdType("hubId", HUB_ID.key)
  val INDEX_ITEM = DelvingIdType("indexItem", ID.key)
  val LEGACY = DelvingIdType("legacy", EUROPEANA_URI.key)
  val FREE = DelvingIdType("free", "")

  // TODO legacy support - to be removed on 01.06.2013
  val ITIN = DelvingIdType("itin", "")

  val types = Seq(SOLR, PMH, DRUPAL, HUB, INDEX_ITEM, LEGACY, FREE, ITIN)

  def apply(idType: String): DelvingIdType = types.find(_.idType == idType).getOrElse(DelvingIdType.HUB)

}

case class SOLRFacetCountLink(facetCount: FacetField.Count, url: String, remove: Boolean) extends FacetCountLink {

  def getValue: String = if (facetCount.getName != null) facetCount.getName else "missing"
  def getCount: Long = facetCount.getCount

  override def toString: String = "<a href='%s'>%s</a> (%s)".format(url, getValue, if (remove) "remove" else "add")
}

case class SOLRFacetQueryLinks(facetName: String, links: List[SOLRFacetCountLink] = List.empty, facetSelected: Boolean = false, missingValues: Int = 0)
    extends FacetQueryLinks {

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

  def put(key: String, values: Seq[String]) { params put (key, values) }

  def all = params

  def allNonEmpty = params

  val allSingle = params.map(params => (params._1, params._2.head)).toMap

  def _contains(key: String) = params.contains(key)

  def valueIsNonEmpty(key: String) = _contains(key) && !getValue(key).isEmpty

  def getFirst(key: String) = getValues(key).headOption

  def getValue(key: String) = getValues(key).head

  def getValues(key: String): Seq[String] = params.get(key).getOrElse(Seq[String]())

  def hasKeyAndValue(key: String, value: String) = _contains(key) && getValue(key).equalsIgnoreCase(value)

  def getValueOrElse(key: String, default: String): String = params.get(key).getOrElse(return default).headOption.getOrElse(default).toString

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

case class CHQuery(
  solrQuery: SolrQuery,
  responseFormat: String = "xml",
  filterQueries: List[FilterQuery] = List.empty,
  hiddenFilterQueries: List[FilterQuery] = List.empty,
  systemQueries: List[String] = List.empty,
  groupField: Option[String] = None)

case class CHResponse(response: QueryResponse, chQuery: CHQuery, configuration: OrganizationConfiguration) { // todo extend with the other response elements

  lazy val breadCrumbs: List[BreadCrumb] = SolrQueryService.createBreadCrumbList(chQuery)

}

case class Pager(numFound: Int, start: Int = 1, rows: Int, pageSize: Int = 12) {

  private val MARGIN: Int = 5
  private val PAGE_NUMBER_THRESHOLD: Int = 7
  val hardenedRows = if (rows == 0) pageSize else rows

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

case class SOLRResultPagination(chResponse: CHResponse) extends ResultPagination {

  lazy val pager = SolrQueryService.createPager(chResponse)(chResponse.configuration)

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

  def getPresentationQuery: PresentationQuery = SOLRPresentationQuery(chResponse)

  def getLastViewablePage: Int = pager.totalPages
}

case class SOLRPresentationQuery(chResponse: CHResponse) extends PresentationQuery {

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
    "query=%s%s".format(SolrQueryService.encodeUrl(solrQuery.getQuery), chResponse.chQuery.filterQueries.mkString("&qf=", "&qf=", ""))
  }

}

case class BriefItemView(chResponse: CHResponse) extends SearchResult {

  def getResultDocuments: List[MetadataAccessors] = getBriefDocs

  def getBriefDocs: List[BriefDocItem] = SolrBindingService.getBriefDocs(chResponse.response)

  def getFacetQueryLinks: List[SOLRFacetQueryLinks] = SolrQueryService.createFacetQueryLinks(chResponse = chResponse)

  val pagination = SOLRResultPagination(chResponse)

  def getPagination: ResultPagination = pagination
}

case class DocItemReference(hubId: String, defaultSchema: String, publicSchemas: Seq[String], relatedItems: Seq[BriefDocItem] = Seq.empty, item: Option[BriefDocItem] = None)