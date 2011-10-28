package controllers.search

import models.PortalTheme
import scala.collection.JavaConversions._
import org.apache.solr.client.solrj.SolrQuery
import play.mvc.Scope.Params
import org.apache.solr.client.solrj.response.FacetField

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/17/11 2:25 PM  
 */

object SearchService {

  import play.mvc.results.Result
  import play.mvc.Http.Request


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
    new RenderXml("<sjoerd>rocks</sjoerd>")
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
