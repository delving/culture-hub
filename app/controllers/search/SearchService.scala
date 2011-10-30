package controllers.search

import models.PortalTheme
import scala.collection.JavaConversions._
import play.mvc.Http.Request
import play.Logger
import play.mvc.results.Result

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/17/11 2:25 PM  
 */
object SearchService {

  def getApiResult(request: Request, theme: PortalTheme) : Result =
    new SearchService(request, theme).getApiResult

}

class SearchService(request: Request, theme: PortalTheme) {

  import play.mvc.results.Result
  import xml.PrettyPrinter

  val prettyPrinter = new PrettyPrinter(150, 5)
  val params = request.params
  val paramMap = params.all()
  val format = paramMap.getOrElse("format", Array[String]("default")).head

  /**
   * This function parses the response for with output format needs to be rendered
   */

  def getApiResult : Result = {

      val response = try {
      if (theme.apiWsKey) {
        val wskey = paramMap.getOrElse("wskey", Array[String]("unknown")).head
        // todo add proper wskey checking
        if (wskey.isEmpty) {
          import models.AccessKeyException
          Logger.warn(String.format("Service Access Key %s invalid!", wskey));
          throw new AccessKeyException(String.format("Access Key %s not accepted", wskey));
        }
      }
      format match {
        case "json" => getJSONResultResponse()
        case "jsonp" =>
          getJSONResultResponse(callback = paramMap.getOrElse("callback", Array[String]("delvingCallback")).head)
          // todo add simile and similep support later
        case _ => getXMLResultResponse()
      }
    }
    catch {
      case ex : Exception =>
        Logger.error(ex, "something went wrong")
        errorResponse(errorMessage = ex.getLocalizedMessage, format = format)
    }
    response
  }

  def getJSONResultResponse(authorized: Boolean = true, callback : String = ""): Result = {
    import play.mvc.results.RenderJson
    import org.apache.solr.client.solrj.SolrQuery
    require(params._contains("query") || params._contains("id") || params._contains("explain"))

    val response : String = paramMap.keys.toList match {
      case x : List[String] if x.contains("explain") => ExplainResponse(theme).renderAsJson
      case x : List[String] if x.contains("id") && !paramMap.get("id").head.isEmpty =>
        val fullItemView = getFullResultsFromSolr
        val response1 = CHResponse(params = params, theme = theme, chQuery = CHQuery(solrQuery = new SolrQuery("*:*"), responseFormat = "json"), response = fullItemView.response)
        FullView(fullItemView, response1).renderAsJSON(authorized)
//      case _ => SearchSummary(getBriefResultsFromSolr, aro).renderAsJSON(authorized)
    }
    new RenderJson(if (!callback.isEmpty) "%s(%s)".format(callback, response) else response)

  }

  def getXMLResultResponse(authorized: Boolean = true): Result  = {
    import xml.Elem
    import play.mvc.results.RenderXml
    require(params._contains("query") || params._contains("id") || params._contains("explain"))

    val response : Elem = paramMap.keys.toList match {
      case x : List[String] if x.contains("explain") => ExplainResponse(theme).renderAsXml
      case x : List[String] if x.contains("id") && !paramMap.get("id").head.isEmpty =>
        import org.apache.solr.client.solrj.SolrQuery
        val fullItemView = getFullResultsFromSolr
        val response1 = CHResponse(params = params, theme = theme, chQuery = CHQuery(solrQuery = new SolrQuery("*:*"), responseFormat = "xml"), response = fullItemView.response)
        FullView(fullItemView, response1).renderAsXML(authorized)
//      case _ => SearchSummary(getBriefResultsFromSolr, aro).renderAsXML(authorized)
    }

    new RenderXml("<?xml version='1.0' encoding='utf-8' ?>\n" + prettyPrinter.format(response))
  }

  // todo implement this
//  private def getBriefResultsFromSolr: BriefBeanView = {
//    val userQuery = aro.request.getParameter("query")
//    require(!userQuery.isEmpty)
//    val jParams = aro.request.getParameterMap.asInstanceOf[JMap[String, Array[String]]]
//    val solrQuery: SolrQuery = SolrQueryUtil.createFromQueryParams(jParams, aro.queryAnalyzer, aro.locale, ThemeFilter.getTheme.getRecordDefinition)
//
//    if (jParams.containsKey("fl")) solrQuery.setFields(jParams.get("fl").headOption.getOrElse("*,score")) else solrQuery.setFields("*,score")
//    if (jParams.contains("facet.limit")) solrQuery.setFacetLimit(Integer.valueOf(jParams.get("facet.limit").headOption.getOrElse("100")))
//
//    val briefResultView = aro.beanQueryModelFactory.getBriefResultView(solrQuery, solrQuery.getQuery, jParams, aro.locale)
//    aro.clickStreamLogger.logApiBriefView(aro.request, briefResultView, solrQuery)
//    briefResultView
//  }

    // todo implement this
  private def getFullResultsFromSolr : FullItemView = {
    import org.apache.solr.client.solrj.SolrQuery
    require(params._contains("id") || params._contains("did"))
    // todo must be coded differently in the future
    val response = SolrQueryService.runQuery(new SolrQuery("europeana_uri:\"%s\"".format(params.get("id"))), theme.solrSelectUrl)
//    val jParams = aro.request.getParameterMap.asInstanceOf[JMap[String, Array[String]]]
//    val fullView = aro.beanQueryModelFactory.getFullResultView(jParams, aro.locale)
//    aro.clickStreamLogger.logApiFullView(aro.request, fullView, idQuery)
    FullItemView(SolrBindingService.getFullDoc(response), response)
  }


   def errorResponse(error : String = "Unable to respond to the API request",
                    errorMessage: String = "Unable to determine the cause of the Failure", format : String = "xml") : Result = {

     import play.mvc.results.{RenderXml, RenderJson}

     def toXML : String = {
      val response =
        <results>
         <error>
           <title>{error}</title>
           <description>{errorMessage}</description>
         </error>
       </results>
    "<?xml version='1.0' encoding='utf-8' ?>\n" + prettyPrinter.format(response)
    }

    def toJSON : String = {
      import net.liftweb.json.JsonAST._
      import net.liftweb.json.{Extraction, Printer}
      import collection.immutable.ListMap
      //      aro.response setContentType ("text/javascript")
      implicit val formats = net.liftweb.json.DefaultFormats
      val docMap = ListMap("status" -> error, "message" -> errorMessage)
      Printer pretty (render(Extraction.decompose(docMap)))
    }

    val response = format match {
       case x : String if x.startsWith("json") || x.startsWith("simile") => new RenderJson(toJSON)
       case _ => new RenderXml(toXML)
     }

//    aro.response setStatus (HttpServletResponse.SC_BAD_REQUEST)
     // todo set error response
    response
  }

}

case class SearchSummary(result : BriefBeanView, language: String = "en", chResponse: CHResponse) {

  import collection.mutable.LinkedHashMap
  import xml.Elem

  private val pagination = result.getPagination
  private val searchTerms = pagination.getPresentationQuery.getUserSubmittedQuery
  private val startPage = pagination.getStart

  def minusAmp(link : String) = link.replaceAll("amp;", "").replaceAll(" ","%20").replaceAll("qf=","qf[]=")

  def localiseKey(metadataField: String, defaultLabel: String = "unknown", language: String = "en"): String = {
    import java.util.Locale
    val locale = new Locale(language)
//    val localizedName: String = aro.lookup.toLocalizedName(metadataField.replace(":", "_"), locale)
    val localizedName: String = metadataField.replace(":", "_")
    if (localizedName != null && !defaultLabel.startsWith("#")) localizedName else defaultLabel
  }

  val layoutMap = LinkedHashMap[String, String]("#thumbnail" -> "europeana:object", "#title" -> "dc:title", "#uri" -> "europeana:uri",
    "#isShownAt" -> "europeana:isShownAt", "#description" -> "dc:description", "Creator" -> "dc:creator",
    "Subject(s)" -> "dc:subject", "County" -> "abm:county", "Municipality" -> "abm:municipality", "Place" -> "abm:namedPlace",
    "Person(s)" -> "abm:aboutPerson")

  def renderAsXML(authorized : Boolean) : Elem = {

    val response : Elem =
      <results xmlns:icn="http://www.icn.nl/" xmlns:europeana="http://www.europeana.eu/schemas/ese/" xmlns:dc="http://purl.org/dc/elements/1.1/"
               xmlns:raw="http://delving.eu/namespaces/raw" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:ese="http://www.europeana.eu/schemas/ese/"
               xmlns:abm="http://to_be_decided/abm/" xmlns:abc="http://www.ab-c.nl/">
        <query numFound={pagination.getNumFound.toString}>
            <terms>{searchTerms}</terms>
            <breadCrumbs>
              {pagination.getBreadcrumbs.map(bc => <breadcrumb field={bc.field} href={minusAmp(bc.href)} value={bc.value}>{bc.display}</breadcrumb>)}
            </breadCrumbs>
        </query>
        <pagination>
            <start>{pagination.getStart}</start>
            <rows>{pagination.getRows}</rows>
            <numFound>{pagination.getNumFound}</numFound>
            {if (pagination.isNext) <nextPage>{pagination.getNextPage}</nextPage>}
            {if (pagination.isPrevious) <previousPage>{pagination.getPreviousPage}</previousPage>}
            <currentPage>{pagination.getStart}</currentPage>
            <links>
              {pagination.getPageLinks.map(pageLink =>
              <link start={pageLink.start.toString} isLinked={pageLink.isLinked.toString}>{pageLink.display}</link>)}
            </links>
        </pagination>
        <layout>
          <drupal>
            {layoutMap.map(item =>
              <field>
                <key>{localiseKey(item._2, item._1, language)}</key>
                <value>{item._2}</value>
              </field>
              )
            }
          </drupal>
        </layout>
        <items>
          {result.getBriefDocs.map(item =>
          <item>
          {item.getFieldValuesFiltered(false, Array("delving_pmhId","europeana:collectionName", "europeana:collectionTitle")).sortWith((fv1, fv2) => fv1.getKey < fv2.getKey).map(field => SolrQueryService.renderXMLFields(field, chResponse))}
          </item>
        )}
        </items>
        <facets>
          {result.getFacetQueryLinks.map(fql =>
            <facet name={fql.facetType} isSelected={fql.facetSelected.toString}>
              {fql.links.map(link =>
                    <link url={minusAmp(link.url)} isSelected={link.remove.toString} value={link.value} count={link.count.toString}>{link.value} ({link.value})</link>
            )}
            </facet>
          )}
        </facets>
      </results>
    response
  }

  def renderAsJSON(authorized : Boolean) : String = {
    import collection.immutable.ListMap
    import net.liftweb.json.{Extraction, JsonAST, Printer}
    implicit val formats = net.liftweb.json.DefaultFormats

    def createJsonRecord(doc : BriefDocItem) : ListMap[String, Any]= {
      val recordMap = collection.mutable.ListMap[String, Any]();
      doc.getFieldValuesFiltered(false, Array("delving_pmhId", "europeana:collectionName", "europeana:collectionTitle"))
                              .sortWith((fv1, fv2) => fv1.getKey < fv2.getKey).foreach(fv => recordMap.put(fv.getKeyAsXml, SolrQueryService.encodeUrl(fv.getValueAsArray, fv.getKey, chResponse)))
      ListMap(recordMap.toSeq: _*)
    }

    def createLayoutItems : ListMap[String, Any] = {
      val recordMap = collection.mutable.ListMap[String, Any]();
      layoutMap.map(item =>
              recordMap.put(localiseKey(item._2, item._1, language), item._2))
      ListMap(recordMap.toSeq: _*)
    }

    def createFacetList: List[ListMap[String, Any]] = {
      result.getFacetQueryLinks.map(fql =>
        ListMap("name" -> fql.facetType, "isSelected" -> fql.facetSelected, "links" -> fql.links.map(link =>
          ListMap("url" -> minusAmp(link.url), "isSelected" -> link.remove, "value" -> link.value, "count" -> link.count, "displayString" -> "%s (%s)".format(link.value, link.count))))
      ).toList
    }

    val outputJson = Printer.pretty(JsonAST.render(Extraction.decompose(
      ListMap("result" ->
              ListMap("query" ->
                      ListMap("numfound" -> pagination.getNumFound, "terms" -> searchTerms,
                        "breadCrumbs" -> pagination.getBreadcrumbs.map(bc => ListMap("field" -> bc.field, "href" -> minusAmp(bc.href), "value" -> bc.display))),
                "pagination" ->
                        ListMap("start" -> pagination.getStart, "rows" -> pagination.getRows, "numFound" -> pagination.getNumFound,
                          "hasNext" -> pagination.isNext, "nextPage" -> pagination.getNextPage, "hasPrevious" -> pagination.isPrevious,
                          "previousPage" -> pagination.getPreviousPage, "currentPage" -> pagination.getStart,
                          "links" -> pagination.getPageLinks.map(pageLink => ListMap("start" -> pageLink.start, "isLinked" -> pageLink.isLinked, "pageNumber" -> pageLink.display))
                        ),
                "layout" ->
                        ListMap[String, Any]("drupal" -> createLayoutItems),
                "items" ->
                        result.getBriefDocs.map(doc => createJsonRecord(doc)).toList,
                "facets" -> createFacetList
              )
      )
    )))
    outputJson
  }
}


case class FullView(fullResult : FullItemView, chResponse: CHResponse) { //

  import xml.Elem

  def renderAsXML(authorized : Boolean) : Elem = {
      val response: Elem =
      <result xmlns:icn="http://www.icn.nl/" xmlns:europeana="http://www.europeana.eu/schemas/ese/" xmlns:dc="http://purl.org/dc/elements/1.1/"
              xmlns:raw="http://delving.eu/namespaces/raw" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:ese="http://www.europeana.eu/schemas/ese/"
              xmlns:abm="http://to_be_decided/abm/" xmlns:abc="http://www.ab-c.nl/">
        <item>
          {for (field <- fullResult.getFullDoc.getFieldValuesFiltered(false, Array("delving_pmhId")).sortWith((fv1, fv2) => fv1.getKey < fv2.getKey)) yield
          SolrQueryService.renderXMLFields(field, chResponse)}
        </item>
      </result>
    response
    }

    def renderAsJSON(authorized : Boolean) : String = {
      import collection.immutable.ListMap
      import net.liftweb.json.{JsonAST, Extraction, Printer}
      implicit val formats = net.liftweb.json.DefaultFormats

      val recordMap = collection.mutable.ListMap[String, Any]()
      fullResult.getFullDoc.getFieldValuesFiltered(false, Array("delving_pmhId","europeana:collectionName", "europeana:collectionTitle"))
                              .sortWith((fv1, fv2) => fv1.getKey < fv2.getKey).foreach(fv => recordMap.put(fv.getKeyAsXml, fv.getValueAsArray))

      val outputJson = Printer.pretty(JsonAST.render(Extraction.decompose(
        ListMap("result" ->
              ListMap("item" -> ListMap(recordMap.toSeq : _*))
        )
      )))
      outputJson
    }
}



case class ExplainItem(label: String, options: List[String] = List(), description: String = "") {

  import xml.Elem
  import collection.immutable.ListMap

  def toXML : Elem = {
    <element>
            <label>{label}</label>
            {if (!options.isEmpty) <options>{options.map(option => <option>{option}</option>)}</options>}
            {if (!description.isEmpty) <description>{description}</description>}
    </element>
  }

  def toJson : ListMap[String, Any] = {
    if (!options.isEmpty && !description.isEmpty)
      ListMap("label" -> label, "options" -> options.toSeq, "description" -> description)
    else if (!options.isEmpty)
      ListMap("label" -> label, "options" -> options.toSeq)
    else
      ListMap("label" -> label)
  }

}

case class ExplainResponse(theme : PortalTheme) {

  import xml.Elem

  val excludeList = List("europeana_unstored", "europeana_source", "europeana_userTag", "europeana_collectionTitle")

  val paramOptions: List[ExplainItem] = List(
    ExplainItem("query", List("any string"), "Will output a summary result set. Any valid Lucene or Solr Query syntax will work."),
    ExplainItem("format", List("xml", "json", "jsonp", "simile", "similep")),
    ExplainItem("cache", List("true", "false"), "Use Services Module cache for retrieving the europeana:object"),
    ExplainItem("id", List("any valid europeana_uri identifier"), "Will output a full-view"),
    ExplainItem("fl", List("any valid search field in a comma-separated list"), "Will only output the specified search fields"),
    ExplainItem("facet.limit", List("Any valid integer. Default is 100"), "Will limit the number of facet entries returned to integer specified."),
    ExplainItem("start", List("any non negative integer")),
    ExplainItem("qf", List("any valid Facet as defined in the facets block")),
    ExplainItem("hqf", List("any valid Facet as defined in the facets block"), "This link is not used for the display part of the API." +
            "It is used to send hidden constraints to the API to create custom API views"),
    ExplainItem("explain", List("all")),
    ExplainItem("sortBy", List("any valid sort field prefixed by 'sort_'"), "When during"),
    ExplainItem("sortOrder", List("asc", "desc"), "The sort order of the field specified by sortBy"),
    ExplainItem("lang", List("any valid iso 2 letter lang codes"), "Feature still experimental. In the future it will allow you to get " +
            "localised strings back for the metadata fields, search fields and facets blocks"),
    ExplainItem("wskey", List("any valid webservices key"), "When the API has been marked as closed")
  )

  def renderAsXml : Elem = {

    <results>
      <api>
       <parameters>
         {paramOptions.map(param => param.toXML)}
        </parameters>
        <search-fields>
          {theme.getRecordDefinition.getFieldNameList.
                filterNot(field => excludeList.contains(field)).map(facet => ExplainItem(facet).toXML)}
        </search-fields>
        <facets>
          {theme.getRecordDefinition.getFacetMap.map(facet => ExplainItem(facet._1).toXML)}
        </facets>
      </api>
    </results>
  }

  def renderAsJson : String = {
    import net.liftweb.json.JsonAST._
    import net.liftweb.json.{Extraction, Printer}
    import scala.collection.immutable.ListMap
    implicit val formats = net.liftweb.json.DefaultFormats

    val outputJson = Printer.pretty(render(Extraction.decompose(
      ListMap("results" ->
              ListMap("api" ->
                     ListMap(
                    "parameters" -> paramOptions.map(param => param.toJson).toIterable,
                        "search-fields" -> theme.getRecordDefinition.getFieldNameList.
                filterNot(field => excludeList.contains(field)).map(facet => ExplainItem(facet).toJson),
                        "facets" -> theme.getRecordDefinition.getFacetMap.map(facet => ExplainItem(facet._1).toJson)))
      ))
    ))
    outputJson
  }
}

case class RecordLabel(name : String, fieldValue : String, multivalued : Boolean = false)