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

import core.indexing.IndexField
import exceptions.AccessKeyException
import core.Constants._
import core.indexing.IndexField._
import play.api.mvc.Results._
import play.api.http.ContentTypes._
import play.api.i18n.{ Lang, Messages }
import collection.JavaConverters._
import play.api.{ Play, Logger }
import Play.current
import play.api.mvc.{ PlainResult, RequestHeader }
import core.ExplainItem
import java.lang.String
import core.rendering._
import models.OrganizationConfiguration
import xml.{ PCData, PrettyPrinter, Elem }
import org.apache.solr.client.solrj.response.FacetField.Count
import org.apache.solr.client.solrj.response.FacetField
import net.liftweb.json.JsonAST._
import net.liftweb.json.{ Extraction, Printer }
import scala.collection.immutable.ListMap
import play.templates.GenericTemplateLoader
import io.Source

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/17/11 2:25 PM
 */
object SearchService {

  def getApiResult(request: RequestHeader, hiddenQueryFilters: Seq[String] = Seq.empty)(implicit configuration: OrganizationConfiguration): PlainResult =
    new SearchService(request, hiddenQueryFilters)(configuration).getApiResult

  def localiseKey(metadataField: String, language: String = "en", defaultLabel: String = "unknown"): String = {
    val localizedName = Messages("metadata." + metadataField.replaceAll("_", "."))(Lang(language))
    if (localizedName != null && !defaultLabel.startsWith("#") && !localizedName.startsWith("metadata.")) localizedName else defaultLabel
  }
}

class SearchService(request: RequestHeader, hiddenQueryFilters: Seq[String] = Seq.empty)(implicit configuration: OrganizationConfiguration) {

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
      if (configuration.searchService.apiWsKeyEnabled) {
        val wsKey = params.getValueOrElse("wskey", "unknown")
        val wsKeyProvided = !wsKey.equalsIgnoreCase("unknown")
        if ((configuration.searchService.apiWsKeyEnabled && !wsKeyProvided) ||
          (configuration.searchService.apiWsKeyEnabled && wsKeyProvided && !configuration.searchService.apiWsKeys.exists(_ == wsKey.trim))) {
          Logger("CultureHub").warn("[%s] Service Access Key %s invalid!".format(configuration.orgId, wsKey))
          throw new AccessKeyException(String.format("Access Key %s not accepted", wsKey))
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
        case "html" if params.valueIsNonEmpty("id") =>
          getRenderedFullView("html", params.getFirst("schema"), false).fold(
            error => errorResponse(error, format),
            view => {
              val template = GenericTemplateLoader.load("tags/view.html")
              val args: java.util.Map[String, Object] = new java.util.HashMap[String, Object]()
              args.put("view", view.toViewTree)
              args.put("_view", view.toViewTree)
              args.put("lang", apiLanguage)
              Ok(template.render(args).replaceAll("""(?m)^\s+""", "")).as(HTML)
            }
          )
        case _ => getXMLResultResponse()
      }
    } catch {
      case t: Throwable =>
        Logger("CultureHub").error("something went wrong", t)
        errorResponse(errorMessage = t.getLocalizedMessage, format = format)
    }
    response
  }

  def getJSONResultResponse(authorized: Boolean = true, callback: String = ""): PlainResult = {

    require(params._contains("query") || params._contains("id") || params._contains("explain"))

    val renderRelatedItems = params.getFirst("mlt").getOrElse("false") == "true"

    val response: String = params match {
      case x if x._contains("explain") && x.getValueOrElse("explain", "nothing").equalsIgnoreCase("fieldValue") => FacetAutoComplete(params, configuration).renderAsJson
      case x if x._contains("explain") => ExplainResponse(params, configuration).renderAsJson
      case x if x.valueIsNonEmpty("id") => getRenderedFullView("api", x.getFirst("schema"), renderRelatedItems) match {
        case Right(rendered) => rendered.toJson
        case Left(error) => return errorResponse("Unable to render full record", error, "json")
      }
      case _ =>
        val briefView = getBriefResultsFromSolr
        SearchSummary(result = briefView, chResponse = briefView.chResponse, language = apiLanguage, request = request).renderAsJSON(authorized)
    }
    Ok(if (!callback.isEmpty) "%s(%s)".format(callback, response) else response).as(JSON)

  }

  def getXMLResultResponse(authorized: Boolean = true): PlainResult = {
    require(params._contains("query") || params._contains("id") || params._contains("explain"))

    val renderRelatedItems = params.getFirst("mlt").getOrElse("false") == "true"

    val response: Elem = params match {
      case x if x._contains("explain") && x.getValueOrElse("explain", "nothing").equalsIgnoreCase("fieldValue") => FacetAutoComplete(params, configuration).renderAsXml
      case x if x._contains("explain") => ExplainResponse(params, configuration).renderAsXml
      case x if x.valueIsNonEmpty("id") => getRenderedFullView("api", x.getFirst("schema"), renderRelatedItems) match {
        case Right(rendered) => return Ok(rendered.toXmlString).as(XML)
        case Left(error) => return errorResponse("Unable to render full record", error, "xml")
      }
      case _ =>
        val briefView = getBriefResultsFromSolr
        val summary = SearchSummary(result = briefView, chResponse = briefView.chResponse, language = apiLanguage, request = request)
        format match {
          case "kml" =>
            summary.renderAsKML(authorized)
          case "kml-a" =>
            summary.renderAsABCKML(authorized)
          case _ =>
            summary.renderAsXML(authorized)
        }
    }

    Ok("<?xml version='1.0' encoding='utf-8' ?>\n" + prettyPrinter.format(response)).as(XML)
  }

  private def getBriefResultsFromSolr: BriefItemView = {
    require(params.valueIsNonEmpty("query"))
    val chQuery = SolrQueryService.createCHQuery(request, additionalSystemHQFs = hiddenQueryFilters)
    BriefItemView(CHResponse(params, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery, configuration))
  }

  private def getRenderedFullView(viewName: String, schema: Option[String] = None, renderRelatedItems: Boolean) = {
    require(params._contains("id"))
    val id = params.getValue("id")
    val idTypeParam = params.getValueOrElse("idType", HUB_ID.key)
    val mltCount = params.getValueOrElse("mlt.count", configuration.searchService.moreLikeThis.count.toString)
    val viewType = ViewType.fromName(viewName)
    RecordRenderer.getRenderedFullView(id, DelvingIdType(idTypeParam), viewType, Lang(apiLanguage), schema, renderRelatedItems, mltCount.toInt, params.queryString)
  }

  def errorResponse(error: String = "Unable to respond to the API request",
    errorMessage: String = "Unable to determine the cause of the Failure", format: String = "xml"): PlainResult = {

    def toXML: String = {
      val response =
        <results>
          <error>
            <title>{ error }</title>
            <description>{ errorMessage }</description>
          </error>
        </results>
      "<?xml version='1.0' encoding='utf-8' ?>\n" + prettyPrinter.format(response)
    }

    def toJSON: String = {
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

  def getSimileResultResponse(callback: String = ""): PlainResult = {
    implicit val formats = net.liftweb.json.DefaultFormats

    try {
      val output: ListMap[String, Any] = if (params.valueIsNonEmpty("id")) {
        // we just don't do SIMILE of full results yet
        return BadRequest
      } else {
        ListMap("items" -> getBriefResultsFromSolr.getBriefDocs.map(doc => renderSimileRecord(doc)))
      }

      val outputJson = Printer.pretty(render(Extraction.decompose(output)))

      if (!callback.isEmpty) {
        Ok("%s(%s)".format(callback, outputJson))
      } else
        Ok(outputJson)
    } catch {
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
        } else {
          recordMap.put(label.name, fieldValue.getFirst) // todo add url encoding later
        }
      }
    }
    )
    ListMap(recordMap.toSeq: _*)
  }
}

case class RecordLabel(name: String, fieldValue: String, multivalued: Boolean = false)

case class SearchSummary(result: BriefItemView, language: String = "en", chResponse: CHResponse, request: RequestHeader) {

  private val pagination = result.getPagination
  private val searchTerms = pagination.getPresentationQuery.getUserSubmittedQuery
  private val filteredFields = Seq("delving_snippet", IndexField.FULL_TEXT.key, "delving_SNIPPET")

  val baseUrl = request.host

  def minusAmp(link: String) = link.replaceAll("amp;", "").replaceAll(" ", "%20").replaceAll("qf=", "qf[]=")

  val briefDocs = result.getBriefDocs

  val filterKeys = List("id", "timestamp", "score")
  // FIXME why are delving fields filtered out here????
  val uniqueKeyNames = result.getBriefDocs.flatMap(doc => doc.solrDocument.getFieldNames).distinct.filterNot(_.startsWith("delving")).filterNot(filterKeys.contains(_)).sortWith(_ > _)
  val uniqueKeyNamesWithDelving = result.getBriefDocs.flatMap(doc => doc.solrDocument.getFieldNames).distinct.filterNot(filterKeys.contains(_)).sortWith(_ > _)

  def translateFacetValue(name: String, value: String) = {
    val listOfFacets = List("europeana_type")
    val cleanLabel = SolrBindingService.stripDynamicFieldLabels(name)
    if (listOfFacets.contains(cleanLabel))
      SearchService.localiseKey("type.%ss".format(value.toLowerCase), language)
    else
      value
  }

  def renderAsABCKML(authorized: Boolean): Elem = {
    def renderData(field: String, fieldName: String, item: BriefDocItem, cdata: Boolean = false, customString: String = "%s"): Elem = {
      if (cdata)
        <Data name={ fieldName }><value>{ PCData(customString.format(item.getAsString(field))) }</value></Data>
      else
        <Data name={ fieldName }><value>{ item.getAsString(field) }</value></Data>

    }

    def renderComposedDescription(item: BriefDocItem): String = {
      def renderStrong(label: String, field: String, out: StringBuffer) {
        val fv = item.getFieldValue(field)
        if (fv.isNotEmpty) out append ("<strong>%s</strong>: %s </br>".format(label, fv.getArrayAsString(", ")))
      }

      val output = new StringBuffer()
      output append ("<strong>%s</strong>".format(item.getAsString("dc_title"))) append ("</br></br>")
      renderStrong("Vervaardiger", "dc_creator", output)
      renderStrong("Soort object", "dc_type", output)
      renderStrong("Vervaardigingsdatum", "dc_date", output)
      renderStrong("Vervaardiging plaats", "dc_coverage", output)
      renderStrong("vindplaats", "icn_location", output)
      renderStrong("Afgebeelde plaats", "dc_subject", output)
      if (item.getFieldValue("dc_coverage").isNotEmpty || item.getFieldValue("icn_location").isNotEmpty) {
        renderStrong("Geassocieerde plaats", "dcterms_spatial", output)
      }
      renderStrong("Afmeting", "dc_format", output)
      renderStrong("Materiaal", "icn_material", output)
      renderStrong("Objectnummer", "dc_identifier", output)
      output append ("</br>")
      output append (item.getAsString("delving_description"))
      output.toString
    }

    def renderDoc(item: BriefDocItem, crd: String, crdNr: String) = {
      <Placemark id={ "%s-%s".format(item.getAsString(HUB_ID.key), crdNr) }>
        <name>{ item.getAsString("delving_title") }</name>
        <Point>
          <coordinates>{ crd.split(",").reverse.mkString(",") }</coordinates>
        </Point>
        {
          if (item.getFieldValue(ADDRESS.key).isNotEmpty) {
            <address>
              { item.getAsString(ADDRESS.key) }
            </address>
          }
        }
        <description>
          { PCData(renderComposedDescription(item)) }
        </description>
        <ExtendedData>
          { renderData("delving_title", "titel", item) }
          { renderData("dcterms_spatial", "ondertitel", item) }
          {
            renderData("delving_landingPage", "bron", item, cdata = true,
              """<a href="%s" target="_blank">Naar online collectie %s</a>""".format(item.getAsString("delving_landingPage"), item.getAsString("delving_owner")))
          }
          { renderData("delving_thumbnail", "thumbnail", item) }
          { renderData("europeana_isShownBy", "image", item) }
        </ExtendedData>
      </Placemark>
    }

    val response: Elem =
      <kml xmlns="http://earth.google.com/kml/2.0">
        <Document>
          {
            briefDocs.map(
              (item: BriefDocItem) =>
                {
                  val coordinates: Array[String] = item.getFieldValue(GEOHASH.key).getValueAsArray
                  coordinates.map(crd => renderDoc(item, crd, coordinates.indexOf(crd).toString))
                }
            )
          }
        </Document>
      </kml>
    response
  }

  def renderAsKML(authorized: Boolean): Elem = {

    // todo remove this hack later
    val sfield = chResponse.params.getValueOrElse("sfield", GEOHASH.key) match {
      case "abm_geo_geohash" => "abm_geo"
      case _ => GEOHASH.key
    }

    val useSchema = false

    val response: Elem =
      <kml xmlns="http://earth.google.com/kml/2.0">
        <Document>
          <Folder>
            <name>Culture-Hub</name>
            {
              if (useSchema) {
                <Schema name="Culture-Hub" id="Culture-HubId">
                  {
                    uniqueKeyNamesWithDelving.map {
                      item =>
                        <SimpleField name={ item } type="string">{ SearchService.localiseKey(item, language) }</SimpleField>
                    }
                  }
                </Schema>
              }
            }
            {
              briefDocs.map(
                (item: BriefDocItem) =>
                  <Placemark id={ item.getAsString(HUB_ID.key) }>
                    <name>{ item.getAsString("delving_title") }</name>
                    <Point>
                      <coordinates>{ item.getAsString(sfield).split(",").reverse.mkString(",") }</coordinates>
                    </Point>
                    <ExtendedData>
                      {
                        if (useSchema) {
                          <SchemaData schemaUrl="#Culture-HubId">
                            { item.toKmFields(filteredFields = filteredFields, language = language).map(field => field) }
                          </SchemaData>
                        } else {
                          List(<Data name='delving:linkHome'><value>{ "http://%s/%s/%s/%s".format(baseUrl, item.getOrgId, item.getSpec, item.getRecordId) }</value></Data>) :::
                            item.toKmFields(filteredFields = filteredFields, language = language, simpleData = useSchema).map(field => field)
                        }
                      }
                    </ExtendedData>
                  </Placemark>
              )
            }
          </Folder>
        </Document>
      </kml>
    response
  }

  def renderAsXML(authorized: Boolean): Elem = {

    // todo add years from query if they exist
    val response: Elem =
      <results xmlns:delving="http://www.delving.eu/schemas/" xmlns:aff="http://schemas.delving.eu/aff/" xmlns:icn="http://www.icn.nl/" xmlns:europeana="http://www.europeana.eu/schemas/ese/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:raw="http://delving.eu/namespaces/raw" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:ese="http://www.europeana.eu/schemas/ese/" xmlns:abm="http://schemas.delving.eu/abm/" xmlns:abc="http://www.ab-c.nl/" xmlns:drup="http://www.itin.nl/drupal" xmlns:itin="http://www.itin.nl/namespace" xmlns:tib="http://www.thuisinbrabant.nl/namespace" xmlns:musip="http://www.musip.nl/" xmlns:custom="http://www.delving.eu/namespaces/custom">
        <query numFound={ pagination.getNumFound.toString } firstYear="0" lastYear="0">
          <terms>{ searchTerms }</terms>
          <breadCrumbs>
            {
              pagination.getBreadcrumbs.map(bc =>
                <breadcrumb field={ bc.field } href={ minusAmp(bc.href) } value={ bc.value } i18n={ SearchService.localiseKey(SolrBindingService.stripDynamicFieldLabels(bc.field), language) }>{ bc.display }</breadcrumb>)
            }
          </breadCrumbs>
        </query>
        <pagination>
          <start>{ pagination.getStart }</start>
          <rows>{ pagination.getRows }</rows>
          <numFound>{ pagination.getNumFound }</numFound>
          {
            if (pagination.isNext) {
              <nextPage>{ pagination.getNextPage }</nextPage>
              <lastPage>{ pagination.getLastViewablePage }</lastPage>
            }
          }
          {
            if (pagination.isPrevious)
              <previousPage>{ pagination.getPreviousPage }</previousPage>
          }<currentPage>
             { pagination.getStart }
           </currentPage>
          <links>
            {
              pagination.getPageLinks.map(pageLink =>
                <link start={ pageLink.start.toString } isLinked={ pageLink.isLinked.toString }>
                  { pageLink.display }
                </link>)
            }
          </links>
        </pagination>
        <layout>
          <fields>
            {
              uniqueKeyNames.map {
                item =>
                  <field>
                    <name>{ item }</name>
                    <i18n>{ SearchService.localiseKey(item, language) }</i18n>
                  </field>
              }
            }
          </fields>
        </layout>
        <items>{ briefDocs.map(item => item.toXml(filteredFields)) }</items>
        <facets>
          {
            result.getFacetQueryLinks.map(fql =>
              <facet name={ fql.getType } isSelected={ fql.facetSelected.toString } i18n={ SearchService.localiseKey(SolrBindingService.stripDynamicFieldLabels(fql.getType), language) } missingDocs={ fql.getMissingValueCount.toString }>
                {
                  fql.links.map(link => {
                    val i18nValue = translateFacetValue(fql.getType, link.value)
                    <link url={ minusAmp(link.url) } isSelected={ link.remove.toString } value={ i18nValue } count={ link.count.toString }>{ i18nValue } ({ link.count.toString })</link>
                  })
                }
              </facet>
            )
          }
        </facets>
      </results>
    response
  }

  def renderAsJSON(authorized: Boolean): String = {
    import net.liftweb.json.{ Extraction, JsonAST, Printer }
    implicit val formats = net.liftweb.json.DefaultFormats

    def createJsonRecord(doc: BriefDocItem): ListMap[String, Any] = {
      val recordMap = collection.mutable.ListMap[String, Any]();
      doc.getFieldValuesFiltered(false, filteredFields)
        .sortWith((fv1, fv2) => fv1.getKey < fv2.getKey).foreach(fv => recordMap.put(fv.getKey, fv.getValueAsArray))
      ListMap("item" ->
        ListMap("fields" ->
          ListMap(recordMap.toSeq: _*)
        )
      )
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
            ListMap[String, Any]("layout" -> uniqueKeyNames.map(item => ListMap("name" -> item, "i18n" -> SearchService.localiseKey(item, language)))),
          "items" ->
            result.getBriefDocs.map(doc => createJsonRecord(doc)).toList,
          "facets" -> createFacetList
        )
      )
    )))
    outputJson
  }
}

case class FacetAutoComplete(params: Params, configuration: OrganizationConfiguration) {
  require(params._contains("field"))
  val facet = params.getValueOrElse("field", "nothing")
  val query = params.getValueOrElse("value", "")
  val rows = try {
    params.getValueOrElse("rows", "10").toInt
  } catch {
    case t: Throwable => 10
  }

  val autocomplete: Seq[Count] = if (facet != "listAll")
    SolrServer.getFacetFieldAutocomplete(facet, query, rows)(configuration)
  else
    SolrServer.getSolrFields(configuration).sortBy(_.name).filter(_.fieldCanBeUsedAsFacet).map(field => new FacetField.Count(new FacetField("facets"), field.name, field.distinct))

  def renderAsXml: Elem = {
    <results>
      {
        autocomplete.map(item =>
          <item count={ item.getCount.toString }>{ item.getName }</item>
        )
      }
    </results>
  }

  def renderAsJson: String = {
    import net.liftweb.json.JsonAST._
    import net.liftweb.json.{ Extraction, Printer }
    import scala.collection.immutable.ListMap
    implicit val formats = net.liftweb.json.DefaultFormats

    val outputJson = Printer.pretty(render(Extraction.decompose(
      ListMap("results" ->
        autocomplete.map(item => ListMap("value" -> item.getName, "count" -> item.getCount)
        )))))
    outputJson
  }

}

case class ExplainResponse(params: Params, configuration: OrganizationConfiguration) {

  val excludeList = List("europeana_unstored", "europeana_source", "europeana_userTag", "europeana_collectionTitle")

  val paramOptions: List[ExplainItem] = List(
    ExplainItem("query", List("any string"), "Will output a summary result set. Any valid Lucene or Solr Query syntax will work."),
    ExplainItem("format", List("xml", "json", "jsonp", "simile", "similep", "kml")),
    ExplainItem("cache", List("true", "false"), "Use Services Module cache for retrieving the europeana:object"),
    ExplainItem("id", List("any valid identifier specified by the idType"), "Will output a full-view. Default idType is hubId taken from the delving_hubId field."),
    ExplainItem("idType", List("hubId", "solr", "mongo", "pmh", "drupal", "datasetId", "legacy"), "//todo complete this"),
    ExplainItem("schema", List("any schema defined in the delving:publicSchemas"), "This parameter is only available when the id is specified as well. It defines the output format for the Full-View. By default the current schema that is used for indexing is rendered."),
    ExplainItem("fl", List("any valid search field in a comma-separated list"), "Will only output the specified search fields"),
    ExplainItem("facet.limit", List("Any valid integer. Default is 100"), "Will limit the number of facet entries returned to integer specified."),
    ExplainItem("facetBoolType", List("AND", "OR", "Default is OR"), "Will determine how the Facet Multiselect functionality is handled within a facet. Between facets it is always AND."),
    ExplainItem("group.field", List("Any valid non-multivalued field"), "This field can be used to return the result grouped by the 'group.field' values."),
    ExplainItem("start", List("any non negative integer")),
    ExplainItem("qf", List("any valid Facet as defined in the facets block")),
    ExplainItem("hqf", List("any valid Facet as defined in the facets block"), "This link is not used for the display part of the API." +
      "It is used to send hidden constraints to the API to create custom API views"),
    ExplainItem("explain", List("all", "light", "fieldValue"), "fieldValue will give you back an autocomplete response when you provide the 'field' to autocomplete on and the 'value' to limit it. Additional parameters are 'rows' for nr returned and format" +
      "when you specify listAll as the field you will get back all the fields that can be used for autocompletion."),
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

  val solrFields = SolrServer.getSolrFields(configuration).sortBy(_.name)
  val solrFieldsWithFacets = solrFields.filter(_.fieldCanBeUsedAsFacet)
  val sortableFields = solrFields.filter(_.fieldIsSortable)

  val explainType = params.getValueOrElse("explain", "light")

  def renderAsXml: Elem = {

    <results>
      <api>
        {
          if (!explainType.equalsIgnoreCase("light"))
            <parameters>
              { paramOptions.map(param => param.toXml) }
            </parameters>
        }
        <solr-dynamic>
          <fields>
            {
              solrFields.map {
                field =>
                  <field xml={ field.xmlFieldName } search={ field.name } fieldType={ field.fieldType } docs={ field.docs.toString } distinct={ field.distinct.toString }>
                    {
                      if (explainType.equalsIgnoreCase("full")) {
                        <topTerms>
                          {
                            field.topTerms.map {
                              term =>
                                <item count={ term.freq.toString }>{ term.name }</item>
                            }
                          }
                        </topTerms>
                        <histoGram>
                          {
                            field.histogram.map {
                              term =>
                                <item count={ term.freq.toString }>{ term.name }</item>
                            }
                          }
                        </histoGram>
                      }
                    }
                  </field>
              }
            }
          </fields>
          <facets>
            {
              solrFieldsWithFacets.map {
                field =>
                  <facet xml={ field.xmlFieldName } search={ field.name } fieldType={ field.fieldType } docs={ field.docs.toString } distinct={ field.distinct.toString }></facet>
              }
            }
          </facets>
          <sort-fields>
            {
              sortableFields.map {
                field =>
                  <sort-field xml={ field.xmlFieldName } search={ field.name } fieldType={ field.fieldType } docs={ field.docs.toString } distinct={ field.distinct.toString }></sort-field>
              }
            }
          </sort-fields>
        </solr-dynamic>
      </api>
    </results>
  }

  def renderAsJson: String = {
    implicit val formats = net.liftweb.json.DefaultFormats

    val outputJson = Printer.pretty(render(Extraction.decompose(
      ListMap("results" ->
        ListMap("api" ->
          ListMap(
            "parameters" -> paramOptions.map(param => param.toJson).toIterable,
            "search-fields" -> solrFields.map(facet => ListMap("search" -> facet.name, "xml" -> facet.xmlFieldName, "distinct" -> facet.distinct.toString,
              "docs" -> facet.docs.toString, "fieldType" -> facet.fieldType)),
            "facets" -> solrFieldsWithFacets.map(facet => ListMap("search" -> facet.name, "xml" -> facet.xmlFieldName, "distinct" -> facet.distinct.toString,
              "docs" -> facet.docs.toString, "fieldType" -> facet.fieldType))
          ))
      ))))
    outputJson
  }
}