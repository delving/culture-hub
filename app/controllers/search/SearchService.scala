package controllers.search

import models.PortalTheme
import scala.collection.JavaConversions._

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/17/11 2:25 PM  
 */

object SearchService {

  import play.mvc.results.Result
  import play.mvc.Http.Request
  import xml.PrettyPrinter

  val prettyPrinter = new PrettyPrinter(150, 5)

  /**
   * This function parses the response for with output format needs to be rendered
   */

  def getApiResult(request: Request) : Result = {
    import play.mvc.results.RenderXml
    import play.mvc.Http.Request
    val format =  request.params.all().getOrElse("format", Array[String]("default")).head

    //    val response = try {
//      if (aro.restrictedApiAccess) {
//        val wskey = aro.params.getOrElse("wskey", Array[String]("unknown")).head
//        if (!aro.accessKey.checkKey(wskey)) {
//          import eu.delving.services.exceptions.AccessKeyException
//          log.warn(String.format("Service Access Key %s invalid!", wskey));
//          throw new AccessKeyException(String.format("Access Key %s not accepted", wskey));
//        }
//      }
//      format match {
//        case "json" => getJSONResultResponse()
//        case "jsonp" =>
//          getJSONResultResponse(callback = aro.params.get("callback").getOrElse(Array[String]("delvingCallback")).head)
//        case "simile" => getSimileResultResponse()
//        case "similep" =>
//          getSimileResultResponse(aro.params.get("callback").getOrElse(Array[String]("delvingCallback")).head)
//        case _ => getXMLResultResponse(true)
//      }
//    }
//    catch {
//      case ex : Exception =>
//        log.error("something went wrong", ex)
//        errorResponse(errorMessage = ex.getLocalizedMessage, format = format)
//    }
//    aro.response setCharacterEncoding ("UTF-8")
//    new RenderXml("<sjoerd>rocks</sjoerd>")
    errorResponse(format = format)
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
                    // "parameters" -> paramOptions.map(param => param.toJson), todo fix this
                        "search-fields" -> theme.getRecordDefinition.getFieldNameList.
                filterNot(field => excludeList.contains(field)).map(facet => ExplainItem(facet).toJson),
                        "facets" -> theme.getRecordDefinition.getFacetMap.map(facet => ExplainItem(facet._1).toJson)))
      ))
    ))
    outputJson
  }
}

case class RecordLabel(name : String, fieldValue : String, multivalued : Boolean = false)