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

import exceptions.AccessKeyException
import core.Constants._
import play.api.mvc.Results._
import play.api.http.ContentTypes._
import play.api.i18n.{Lang, Messages}
import scala.collection.JavaConverters._
import play.api.Logger
import scala.collection.immutable.ListMap
import play.api.mvc.{PlainResult, RequestHeader}
import core.ExplainItem
import java.lang.String
import models.{IndexItem, RecordDefinition, MetadataRecord, PortalTheme}
import scala.xml.{NodeSeq, Elem}
import core.rendering.{RenderNode, RenderedView, ViewRenderer}
import scala._
import org.apache.solr.client.solrj.response.FacetField.Count
import org.apache.solr.client.solrj.response.FacetField
import java.net.{URLEncoder, URLDecoder}

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/17/11 2:25 PM
 */
object SearchService {

  def getApiResult(orgId: Option[String], request: RequestHeader, theme: PortalTheme, hiddenQueryFilters: List[String] = List.empty): PlainResult =
    new SearchService(orgId, request, theme, hiddenQueryFilters).getApiResult


  def localiseKey(metadataField: String, language: String = "en", defaultLabel: String = "unknown"): String = {
    val localizedName = Messages("metadata." + metadataField.replaceAll("_", "."))(Lang(language))
    if (localizedName != null && !defaultLabel.startsWith("#") && !localizedName.startsWith("metadata.")) localizedName else defaultLabel
  }
}

class SearchService(orgId: Option[String], request: RequestHeader, theme: PortalTheme, hiddenQueryFilters: List[String] = List.empty) {

  import scala.xml.PrettyPrinter
  import java.lang.String

  val log = Logger("CultureHub")

  val prettyPrinter = new PrettyPrinter(200, 5)
  val params = Params(request.queryString)
  val format = params.getValueOrElse("format", "default")
  val apiLanguage = params.getValueOrElse("lang", "en")

  /**
   * This function parses the response for with output format needs to be rendered
   */

  def getApiResult: PlainResult = {

    val response = try {
      if (theme.apiWsKey) {
        val wskey = params.getValueOrElse("wskey", "unknown") //paramMap.getOrElse("wskey", Array[String]("unknown")).head
        // todo add proper wskey checking
        if (!wskey.toString.equalsIgnoreCase("unknown")) {
          Logger.warn(String.format("Service Access Key %s invalid!", wskey));
          throw new AccessKeyException(String.format("Access Key %s not accepted", wskey));
        }
      }
      format match {
        case "json" => getJSONResultResponse()
        case "jsonp" =>
          getJSONResultResponse(callback = params.getValueOrElse("callback", "delvingCallback"))
        // todo add simile and similep support later
        case "simile" => getSimileResultResponse()
        case "similep" =>
          getSimileResultResponse(callback = params.getValueOrElse("callback", "delvingCallback"))
        case _ => getXMLResultResponse()
      }
    }
    catch {
      case ex: Exception =>
        Logger("CultureHub").error("something went wrong", ex)
        errorResponse(errorMessage = ex.getLocalizedMessage, format = format)
    }
    response
  }

  def getJSONResultResponse(authorized: Boolean = true, callback: String = ""): PlainResult = {

    require(params._contains("query") || params._contains("id") || params._contains("explain"))

    val response: String = params match {
      case x if x._contains("explain") && x.getValueOrElse("explain", "nothing").equalsIgnoreCase("fieldValue") => FacetAutoComplete(params).renderAsJson
      case x if x._contains("explain") => ExplainResponse(theme, params).renderAsJson
      case x if x.valueIsNonEmpty("id") => getRenderedFullView("full", x.getFirst("schema")) match {
        case Some(rendered) => rendered.toJson
        case None if x.getFirst("schema").isDefined => return errorResponse("Record Not Found", "Unable to find record for id: %s with schema: %s".format(x.getValueOrElse("id", "unknown key"), x.getFirst("schema").get), "json")
        case None => return errorResponse("Record Not Found", "Unable to find record for id: %s".format(x.getValueOrElse("id", "unknown key")), "json")
      }
      case _ =>
        val briefView = getBriefResultsFromSolr
        SearchSummary(result = briefView, chResponse = briefView.chResponse, language = apiLanguage).renderAsJSON(authorized)
    }
    Ok(if (!callback.isEmpty) "%s(%s)".format(callback, response) else response).as(JSON)

  }

  def getXMLResultResponse(authorized: Boolean = true): PlainResult = {
    import scala.xml.Elem
    require(params._contains("query") || params._contains("id") || params._contains("explain"))

    val response: Elem = params match {
      case x if x._contains("explain") && x.getValueOrElse ("explain", "nothing").equalsIgnoreCase("fieldValue") => FacetAutoComplete(params).renderAsXml
      case x if x._contains("explain") => ExplainResponse(theme, params).renderAsXml
      case x if x.valueIsNonEmpty("id") => getRenderedFullView("full", x.getFirst("schema")) match {
          case Some(rendered) => return Ok(rendered.toXmlString).as(XML)
          case None if x.getFirst("schema").isDefined => return errorResponse("Record Not Found", "Unable to find record for id: %s with schema: %s".format(x.getValueOrElse("id", "unknown key"), x.getFirst("schema").get), "xml")
          case None => return errorResponse("Record Not Found", "Unable to find record for id: %s".format(x.getValueOrElse("id", "unknown key")), "xml")
      }
      case _ =>
        val briefView = getBriefResultsFromSolr
        SearchSummary(result = briefView, chResponse = briefView.chResponse, language = apiLanguage).renderAsXML(authorized)
    }

    Ok("<?xml version='1.0' encoding='utf-8' ?>\n" + prettyPrinter.format(response)).as(XML)
  }

  private def getBriefResultsFromSolr: BriefItemView = {
    require(params.valueIsNonEmpty("query"))
    val chQuery = SolrQueryService.createCHQuery(request, theme, true, additionalSystemHQFs = hiddenQueryFilters)
    BriefItemView(CHResponse(params, theme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery))
  }

  def getRenderedFullView(viewName: String, schema: Option[String] = None): Option[RenderedView] = {
    require(params._contains("id"))
    val id = params.getValue("id")
    val idType = params.getValueOrElse("idType", HUB_ID)
    SolrQueryService.resolveHubIdAndFormat(orgId, URLEncoder.encode(id, "utf-8"), idType) match {
      case Some((hubId, defaultSchema, publicSchemas)) =>
        val maybePrefix = if(schema.isDefined && publicSchemas.contains(schema.get)) {
          Some(schema.get)
        } else if(schema.isDefined && !publicSchemas.contains(schema.get)) {
          Logger("Search").info("Schema %s not available for hubId %s".format(schema.get, hubId))
          None
        } else {
          Some(defaultSchema)
        }

        if(maybePrefix.isEmpty) {
          Logger("Search").info("Could not find prefix for rendering of full view of record %s".format(hubId))
          None
        } else {
          if(idType == "indexItem") {
            renderIndexItem(id)
          } else {
            renderMetadataRecord(maybePrefix.get, hubId, viewName)
          }
        }
      case None =>
        None
    }
  }

  private def renderIndexItem(id: String) = IndexItem.findOneById(id).map {
    indexItem =>
      new RenderedView {
        def toXmlString: String = indexItem.rawXml
        def toJson: String = "JSON rendering not supported"
        def toXml: NodeSeq = scala.xml.XML.loadString(indexItem.rawXml)
        def toViewTree: RenderNode = null
      }
  }

  private def renderMetadataRecord(prefix: String, hubId: String, viewName: String): Option[RenderedView] = {
    val rawRecord: Option[String] = MetadataRecord.getMDR(URLDecoder.decode(hubId, "utf-8")).flatMap(_.getCachedTransformedRecord(prefix))
    if (rawRecord.isEmpty) {
      Logger("Search").info("Could not find cached record in mongo with format %s for hubId %s".format(prefix, hubId))
      None
    } else {

      // handle legacy formats
      val legacyFormats = List("tib", "icn", "abm", "ese", "abc")
      val viewDefinitionFormatName = if (legacyFormats.contains(prefix)) "legacy" else prefix

      // let's do some rendering
      RecordDefinition.getRecordDefinition(prefix) match {
        case Some(definition) =>
          val viewRenderer = ViewRenderer.fromDefinition(viewDefinitionFormatName, viewName)
          if (viewRenderer.isEmpty) {
            log.warn("Tried rendering full record with id '%s' for non-existing view type '%s'".format(hubId, viewName))
            None
          } else {
            try {
              val cleanRawRecord = rawRecord.get.replaceFirst("<\\?xml.*?>", "")
              log.debug(cleanRawRecord)
              val wrappedRecord = "<root %s>%s</root>".format(definition.getNamespaces.map(ns => "xmlns:" + ns._1 + "=\"" + ns._2 + "\"").mkString(" "), cleanRawRecord)
              // TODO see what to do with roles
              Some(viewRenderer.get.renderRecord(wrappedRecord, List.empty, definition.getNamespaces, Lang(apiLanguage)))
            } catch {
              case t =>
                log.error("Exception while rendering view %s for record %s".format(viewDefinitionFormatName, hubId), t)
                None
            }
          }
        case None =>
          log.error("While rendering view %s for record %s: could not find record definition with prefix %s".format(viewDefinitionFormatName, hubId, prefix))
          None
      }
    }
  }

  def errorResponse(error: String = "Unable to respond to the API request",
                    errorMessage: String = "Unable to determine the cause of the Failure", format: String = "xml"): PlainResult = {

    def toXML: String = {
      val response =
        <results>
          <error>
            <title>{error}</title>
            <description>{errorMessage}</description>
          </error>
        </results>
      "<?xml version='1.0' encoding='utf-8' ?>\n" + prettyPrinter.format(response)
    }

    def toJSON: String = {
      import net.liftweb.json.JsonAST._
      import net.liftweb.json.{Extraction, Printer}
      import scala.collection.immutable.ListMap
      implicit val formats = net.liftweb.json.DefaultFormats
      val docMap = ListMap("status" -> error, "message" -> errorMessage)
      Printer pretty (render(Extraction.decompose(docMap)))
    }

    val response = format match {
      case x: String if x.startsWith("json") || x.startsWith("simile") => BadRequest(toJSON).as(JSON)
      case _ => BadRequest(toXML).as(XML)
    }

    response
  }

  def getSimileResultResponse(callback : String = "") : PlainResult  = {
    import net.liftweb.json.JsonAST._
    import net.liftweb.json.{Extraction, Printer}
    import scala.collection.immutable.ListMap
    implicit val formats = net.liftweb.json.DefaultFormats

    try {
      val output : ListMap[String, Any] = if (params.valueIsNonEmpty("id")) {
        // we just don't do SIMILE of full results yet
        return BadRequest
      } else {
        ListMap("items" -> getBriefResultsFromSolr.getBriefDocs.map(doc => renderSimileRecord(doc)))
      }

      val outputJson = Printer.pretty(render(Extraction.decompose(output)))

      if (!callback.isEmpty) {
        Ok("%s(%s)".format(callback, outputJson))
      }
      else
        Ok(outputJson)
    }
    catch {
      case ex: Exception =>
        Logger("CultureHub").error("something went wrong", ex)
        errorResponse(errorMessage = ex.getMessage, format = "json")
    }
  }

  def renderSimileRecord(doc: BriefDocItem): ListMap[String, Any] = {

    val recordMap = collection.mutable.ListMap[String, Any]()
    val labelPairs = List[RecordLabel](
      RecordLabel("type", "europeana_type"), RecordLabel("label", "dc_title"), RecordLabel("id", "dc_identifier"),
      RecordLabel("link", "europeana_isShownAt"), RecordLabel("county", "abm_county"),
      RecordLabel("geography", "dcterms_spatial", true), RecordLabel("thumbnail", "europeana_object"),
      RecordLabel("description", "dc_description", true), RecordLabel("created", "dcterms_created"),
      RecordLabel("municipality", "abm_municipality"), RecordLabel("pid", "europeana_uri")
    )

    labelPairs.sortBy(label => label.name < label.name).foreach(label => {
      val fieldValue = doc.getFieldValue(label.fieldValue)
      if (fieldValue.isNotEmpty) {
        if (label.multivalued) {
          recordMap.put(label.name, fieldValue.getValueAsArray)
        }
        else {
          recordMap.put(label.name, fieldValue.getFirst) // todo add url encoding later
        }
      }
    }
    )
    ListMap(recordMap.toSeq : _*)
  }
}

case class RecordLabel(name : String, fieldValue : String, multivalued : Boolean = false)

case class SearchSummary(result: BriefItemView, language: String = "en", chResponse: CHResponse) {

  import scala.xml.Elem

  private val pagination = result.getPagination
  private val searchTerms = pagination.getPresentationQuery.getUserSubmittedQuery
  private val filteredFields = Array(OWNER, CREATOR, SYSTEM_TYPE, "delving_snippet", "delving_fullText", "delving_fullTextObjectUrl")

  def minusAmp(link: String) = link.replaceAll("amp;", "").replaceAll(" ", "%20").replaceAll("qf=", "qf[]=")

  val briefDocs = result.getBriefDocs

  val filterKeys = List("id", "timestamp", "score")
  val uniqueKeyNames = result.getBriefDocs.flatMap(doc => doc.solrDocument.getFieldNames).distinct.filterNot(_.startsWith("delving")).filterNot(filterKeys.contains(_)).sortWith(_ > _)

  def translateFacetValue(name: String, value: String) = {
    val listOfFacets = List("europeana_type")
    val cleanLabel = SolrBindingService.stripDynamicFieldLabels(name)
    if (listOfFacets.contains(cleanLabel))
      SearchService.localiseKey("type.%ss".format(value.toLowerCase), language)
    else
      value
  }

  def renderAsXML(authorized: Boolean): Elem = {

    // todo add years from query if they exist
    val response: Elem =
      <results xmlns:icn="http://www.icn.nl/" xmlns:europeana="http://www.europeana.eu/schemas/ese/" xmlns:dc="http://purl.org/dc/elements/1.1/"
               xmlns:raw="http://delving.eu/namespaces/raw" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:ese="http://www.europeana.eu/schemas/ese/"
               xmlns:abm="http://to_be_decided/abm/" xmlns:abc="http://www.ab-c.nl/" xmlns:delving="http://www.delving.eu/schemas/"
               xmlns:drup="http://www.itin.nl/drupal" xmlns:itin="http://www.itin.nl/namespace" xmlns:tib="http://www.thuisinbrabant.nl/namespace"
               xmlns:custom="http://www.delving.eu/namespaces/custom">
        <query numFound={pagination.getNumFound.toString} firstYear="0" lastYear="0">
          <terms>{searchTerms}</terms>
          <breadCrumbs>
              {pagination.getBreadcrumbs.map(bc =>
            <breadcrumb field={bc.field} href={minusAmp(bc.href)} value={bc.value} i18n={SearchService.localiseKey(SolrBindingService.stripDynamicFieldLabels(bc.field), language)}>{bc.display}</breadcrumb>)}
          </breadCrumbs>
        </query>
        <pagination>
          <start>{pagination.getStart}</start>
          <rows>{pagination.getRows}</rows>
          <numFound>{pagination.getNumFound}</numFound>
          {if (pagination.isNext) {
          <nextPage>{pagination.getNextPage}</nextPage>
          <lastPage>{pagination.getLastViewablePage}</lastPage>
          }}
          {if (pagination.isPrevious)
          <previousPage>{pagination.getPreviousPage}</previousPage>}<currentPage>
          {pagination.getStart}
        </currentPage>

          <links>
            {pagination.getPageLinks.map(pageLink =>
            <link start={pageLink.start.toString} isLinked={pageLink.isLinked.toString}>
              {pageLink.display}
            </link>)}
          </links>
        </pagination>
        <layout>
          <fields>
            {uniqueKeyNames.map {
            item =>
              <field>
                <key>{SearchService.localiseKey(item, language)}</key>
                <value>{item}</value>
              </field>
          }}
          </fields>
        </layout>
        <items>
          {briefDocs.map(item =>
          <item>
            <fields>
              {item.getFieldValuesFiltered(false, filteredFields).sortWith((fv1, fv2) => fv1.getKey < fv2.getKey).map(field => SolrQueryService.renderXMLFields(field, chResponse))}
            </fields>{if (item.getHighlights.isEmpty) <highlights/>
          else
            <highlights>
              {item.getHighlights.map(field => SolrQueryService.renderHighLightXMLFields(field, chResponse))}
            </highlights>}
          </item>
        )}
        </items>
        <facets>
          {result.getFacetQueryLinks.map(fql =>
          <facet name={fql.getType} isSelected={fql.facetSelected.toString} i18n={SearchService.localiseKey(SolrBindingService.stripDynamicFieldLabels(fql.getType), language)} missingDocs={fql.getMissingValueCount.toString}>
            {fql.links.map(link => {
            val i18nValue = translateFacetValue(fql.getType, link.value)
            <link url={minusAmp(link.url)} isSelected={link.remove.toString} value={i18nValue} count={link.count.toString}>{i18nValue} ({link.count.toString})</link>
            })}
          </facet>
        )}
        </facets>
      </results>
    response
  }

  def renderAsJSON(authorized: Boolean): String = {
    import scala.collection.immutable.ListMap
    import net.liftweb.json.{Extraction, JsonAST, Printer}
    implicit val formats = net.liftweb.json.DefaultFormats

    def createJsonRecord(doc: BriefDocItem): ListMap[String, Any] = {
      val recordMap = collection.mutable.ListMap[String, Any]();
      doc.getFieldValuesFiltered(false, Array())
        .sortWith((fv1, fv2) => fv1.getKey < fv2.getKey).foreach(fv => recordMap.put(fv.getKey, fv.getValueAsArray))
      ListMap(recordMap.toSeq: _*)
    }

    def createFacetList: List[ListMap[String, Any]] = {
      result.getFacetQueryLinks.map(fql =>
        ListMap("name" -> fql.getType, "isSelected" -> fql.facetSelected, "i18n" -> SearchService.localiseKey(fql.getType.replaceAll("_facet", "").replaceAll("_", "."), language), "links" -> fql.links.map(link =>
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
            ListMap[String, Any]("layout" -> uniqueKeyNames.map(item => ListMap("key" -> SearchService.localiseKey(item, language), "value" -> item))),
          "items" ->
            result.getBriefDocs.map(doc => createJsonRecord(doc)).toList,
          "facets" -> createFacetList
        )
      )
    )))
    outputJson
  }
}

case class FacetAutoComplete(params: Params) {
  require(params._contains("field"))
  val facet = params.getValueOrElse("field", "nothing")
  val query = params.getValueOrElse("value", "")
  val rows = try {
    params.getValueOrElse("rows", "10").toInt
  }
  catch {
    case _ => 10
  }

  val autocomplete: Seq[Count] =  if (facet != "listAll")
    SolrServer.getFacetFieldAutocomplete(facet, query, rows)
  else
    SolrServer.getSolrFields.sortBy(_.name).filter(_.fieldCanBeUsedAsFacet).map(field => new FacetField.Count(new FacetField("facets"), field.name, field.distinct))

  def renderAsXml : Elem = {
    <results>
      {
      autocomplete.map(item =>
          <item count={item.getCount.toString}>{item.getName}</item>
      )
      }
    </results>
  }

  def renderAsJson : String = {
    import net.liftweb.json.JsonAST._
    import net.liftweb.json.{Extraction, Printer}
    import scala.collection.immutable.ListMap
    implicit val formats = net.liftweb.json.DefaultFormats

    val outputJson = Printer.pretty(render(Extraction.decompose(
      ListMap("results" ->
          autocomplete.map(item => ListMap("value" -> item.getName, "count" -> item.getCount)
    )))))
    outputJson
  }

}

case class ExplainResponse(theme: PortalTheme, params: Params) {

  import scala.xml.Elem

  val excludeList = List("europeana_unstored", "europeana_source", "europeana_userTag", "europeana_collectionTitle")

  val paramOptions: List[ExplainItem] = List(
    ExplainItem("query", List("any string"), "Will output a summary result set. Any valid Lucene or Solr Query syntax will work."),
    ExplainItem("format", List("xml", "json", "jsonp", "simile", "similep")),
    ExplainItem("cache", List("true", "false"), "Use Services Module cache for retrieving the europeana:object"),
    ExplainItem("id", List("any valid europeana_uri identifier"), "Will output a full-view"),
    ExplainItem("idType", List("solr", "mongo", "pmh", "drupal", "datasetId", "legacy"), "//todo complete this"),
    ExplainItem("schema", List("any schema defined in the delving:publicSchemas"), "This parameter is only available when the id is specified as well. It defines the output format for the Full-View. By default the current schema that is used for indexing is rendered."),
    ExplainItem("fl", List("any valid search field in a comma-separated list"), "Will only output the specified search fields"),
    ExplainItem("facet.limit", List("Any valid integer. Default is 100"), "Will limit the number of facet entries returned to integer specified."),
    ExplainItem("facetBoolType", List("AND", "OR", "Default is OR"), "Will determine how the Facet Multiselect functionality is handled within a facet. Between facets it is always AND."),
    ExplainItem("start", List("any non negative integer")),
    ExplainItem("qf", List("any valid Facet as defined in the facets block")),
    ExplainItem("hqf", List("any valid Facet as defined in the facets block"), "This link is not used for the display part of the API." +
      "It is used to send hidden constraints to the API to create custom API views"),
    ExplainItem("explain", List("all", "light", "fieldValue"), "fieldValue will give you back an autocomplete response when you provide the 'field' to autocomplete on and the 'value' to limit it. Additional parameters are 'rows' for nr returned and format" +
      "when you specify listAll as the field you will get back all the fields that can be used for autocompletion." ),
    ExplainItem("mlt", List("true", "false"), "This enables the related item search functionality in combination with requesting a record via the 'id' parameter."),
    ExplainItem("sortBy", List("any valid sort field prefixed by 'sort_'", "geodist()"), "Geodist is can be used to sort the results by distance."),
    ExplainItem("sortOrder", List("asc", "desc"), "The sort order of the field specified by sortBy"),
    ExplainItem("lang", List("any valid iso 2 letter lang codes"), "Feature still experimental. In the future it will allow you to get " +
      "localised strings back for the metadata fields, search fields and facets blocks"),
    ExplainItem("wskey", List("any valid webservices key"), "When the API has been marked as closed"),
    ExplainItem("geoType", List("bbox", "geofilt"), "Type of geosearch. Default = geofilt"),
    ExplainItem("d", List("any non negative integer"), "When this is specified, the geosearch is limited to this range in kilometers. Default = 5"),
    ExplainItem("pt", List("Standard latitude longitude separeded by a comma"), "The point around which the geo-search is executed with the type of query specified by geoType")
  )

  val solrFields = SolrServer.getSolrFields.sortBy(_.name)
  val solrFieldsWithFacets = solrFields.filter(_.fieldCanBeUsedAsFacet)
  val sortableFields = solrFields.filter(_.fieldIsSortable)

  val explainType = params.getValueOrElse("explain", "light")

  def renderAsXml: Elem = {

    <results>
      <api>
        { if (!explainType.equalsIgnoreCase("light"))
        <parameters>
          {paramOptions.map(param => param.toXml)}
        </parameters>
        }
        <solr-dynamic>
          <fields>
            {solrFields.map {
            field =>
              <field xml={field.xmlFieldName} search={field.name} fieldType={field.fieldType} docs={field.docs.toString} distinct={field.distinct.toString}>
                { if (explainType.equalsIgnoreCase("full")) {
                <topTerms>
                  {field.topTerms.map {
                  term =>
                    <item count={term.freq.toString}>{term.name}</item>
                }}
                </topTerms>
                <histoGram>
                  {field.histogram.map {
                  term =>
                    <item count={term.freq.toString}>{term.name}</item>
                }}
                </histoGram>
              }}
              </field>
          }}
          </fields>
          <facets>
            {solrFieldsWithFacets.map {
            field =>
              <facet xml={field.xmlFieldName} search={field.name} fieldType={field.fieldType} docs={field.docs.toString} distinct={field.distinct.toString}></facet>
          }}
          </facets>
          <sort-fields>
            {sortableFields.map {
            field =>
              <sort-field xml={field.xmlFieldName} search={field.name} fieldType={field.fieldType} docs={field.docs.toString} distinct={field.distinct.toString}></sort-field>
          }}
          </sort-fields>
        </solr-dynamic>
      </api>
    </results>
  }

  def renderAsJson: String = {
    import net.liftweb.json.JsonAST._
    import net.liftweb.json.{Extraction, Printer}
    import scala.collection.immutable.ListMap
    implicit val formats = net.liftweb.json.DefaultFormats

    val outputJson = Printer.pretty(render(Extraction.decompose(
      ListMap("results" ->
        ListMap("api" ->
          ListMap(
            "parameters" -> paramOptions.map(param => param.toJson).toIterable,
            "search-fields" -> solrFields.map(facet =>  ListMap("search" -> facet.name, "xml" -> facet.xmlFieldName, "distinct" -> facet.distinct.toString,
              "docs" -> facet.docs.toString, "fieldType" -> facet.fieldType)),
            "facets" -> solrFieldsWithFacets.map(facet => ListMap("search" -> facet.name, "xml" -> facet.xmlFieldName, "distinct" -> facet.distinct.toString,
              "docs" -> facet.docs.toString, "fieldType" -> facet.fieldType))
      ))
    ))))
    outputJson
  }
}