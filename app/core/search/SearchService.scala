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

import models.PortalTheme
import play.Logger
import util.Constants._
import exceptions.AccessKeyException
import play.api.mvc.Results._
import play.api.http.ContentTypes._
import play.api.mvc.{RequestHeader, Result}
import play.api.i18n.{Lang, Messages}

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/17/11 2:25 PM
 */
object SearchService {

  def getApiResult(request: RequestHeader, theme: PortalTheme, hiddenQueryFilters: List[String] = List.empty): Result =
    new SearchService(request, theme, hiddenQueryFilters).getApiResult


  def localiseKey(metadataField: String, language: String = "en", defaultLabel: String = "unknown"): String = {
    val localizedName = Messages("metadata." + metadataField.replaceAll("_", "."))(Lang(language))
    if (localizedName != null && !defaultLabel.startsWith("#")) localizedName else defaultLabel
  }
}

class SearchService(request: RequestHeader, theme: PortalTheme, hiddenQueryFilters: List[String] = List.empty) {

  import xml.PrettyPrinter
  import java.lang.String

  val prettyPrinter = new PrettyPrinter(150, 5)
  val params = Params(request.queryString)
  val format = params.getValueOrElse("format", "default")
  val apiLanguage = params.getValueOrElse("lang", "en")

  /**
   * This function parses the response for with output format needs to be rendered
   */

  def getApiResult: Result = {

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
        case _ => getXMLResultResponse()
      }
    }
    catch {
      case ex: Exception =>
        Logger.error("something went wrong", ex)
        errorResponse(errorMessage = ex.getLocalizedMessage, format = format)
    }
    response
  }

  def getJSONResultResponse(authorized: Boolean = true, callback: String = ""): Result = {

    import org.apache.solr.client.solrj.SolrQuery
    require(params._contains("query") || params._contains("id") || params._contains("explain"))

    val response: String = params match {
      case x if x._contains("explain") => ExplainResponse(theme).renderAsJson
      case x if x.valueIsNonEmpty("id") =>
        val fullItemView = getFullResultsFromSolr
        val response1 = CHResponse(params = params, theme = theme, chQuery = CHQuery(solrQuery = new SolrQuery("*:*"), responseFormat = "json"), response = fullItemView.response)
        FullView(fullItemView, apiLanguage, response1).renderAsJSON(authorized)
      case _ =>
        val briefView = getBriefResultsFromSolr
        SearchSummary(result = briefView, chResponse = briefView.chResponse, language = apiLanguage).renderAsJSON(authorized)
    }
    Ok(if (!callback.isEmpty) "%s(%s)".format(callback, response) else response).as(JSON)

  }

  def getXMLResultResponse(authorized: Boolean = true): Result = {
    import xml.Elem
    require(params._contains("query") || params._contains("id") || params._contains("explain"))

    val response: Elem = params match {
      case x if x._contains("explain") => ExplainResponse(theme).renderAsXml
      case x if x.valueIsNonEmpty("id") =>
        import org.apache.solr.client.solrj.SolrQuery
        val fullItemView = getFullResultsFromSolr
        val response1 = CHResponse(params = params, theme = theme, chQuery = CHQuery(solrQuery = new SolrQuery("*:*"), responseFormat = "xml"), response = fullItemView.response)
        FullView(fullItemView, apiLanguage, response1).renderAsXML(authorized)
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

  private def getFullResultsFromSolr: FullItemView = {
    require(params._contains("id"))
    val idType = params.getValueOrElse("idType", PMH_ID)
    val response = if (params.hasKeyAndValue("mlt", "true")) SolrQueryService.getFullSolrResponseFromServer(params.getValue("id"), idType, true)
    else SolrQueryService.getFullSolrResponseFromServer(params.getValue("id"), idType)
    FullItemView(SolrBindingService.getFullDoc(response), response)
  }


  def errorResponse(error: String = "Unable to respond to the API request",
                    errorMessage: String = "Unable to determine the cause of the Failure", format: String = "xml"): Result = {

    def toXML: String = {
      val response =
        <results>
          <error>
            <title>
              {error}
            </title>
            <description>
              {errorMessage}
            </description>
          </error>
        </results>
      "<?xml version='1.0' encoding='utf-8' ?>\n" + prettyPrinter.format(response)
    }

    def toJSON: String = {
      import net.liftweb.json.JsonAST._
      import net.liftweb.json.{Extraction, Printer}
      import collection.immutable.ListMap
      //      aro.response setContentType ("text/javascript")
      implicit val formats = net.liftweb.json.DefaultFormats
      val docMap = ListMap("status" -> error, "message" -> errorMessage)
      Printer pretty (render(Extraction.decompose(docMap)))
    }

    val response = format match {
      case x: String if x.startsWith("json") || x.startsWith("simile") => Ok(toJSON).as(JSON)
      case _ => Ok(toXML).as(XML)
    }

    //    aro.response setStatus (HttpServletResponse.SC_BAD_REQUEST)
    // todo set error response
    response
  }

}

case class SearchSummary(result: BriefItemView, language: String = "en", chResponse: CHResponse) {

  import collection.mutable.LinkedHashMap
  import xml.Elem

  private val pagination = result.getPagination
  private val searchTerms = pagination.getPresentationQuery.getUserSubmittedQuery
  private val filteredFields = Array("delving_title", "delving_description", "delving_owner", "delving_creator", "delving_snippet", "delving_fullText", "delving_fullTextObjectUrl")

  def minusAmp(link: String) = link.replaceAll("amp;", "").replaceAll(" ", "%20").replaceAll("qf=", "qf[]=")

  val briefDocs = result.getBriefDocs

  val filterKeys = List("id", "timestamp", "score")
  val uniqueKeyNames = result.getBriefDocs.flatMap(doc => doc.solrDocument.getFieldNames).distinct.filterNot(_.startsWith("delving")).filterNot(filterKeys.contains(_)).sortWith(_ > _)

  val drupalLayoutMap = LinkedHashMap[String, String]("#thumbnail" -> "europeana_object", "#title" -> "dc_title", "#uri" -> "europeana_uri",
    "#isShownAt" -> "europeana_isShownAt", "#description" -> "dc_description", "Creator" -> "dc_creator",
    "Subject(s)" -> "dc_subject", "County" -> "abm_county", "Municipality" -> "abm_municipality", "Place" -> "abm_namedPlace",
    "Person(s)" -> "abm_aboutPerson")

  def renderAsXML(authorized: Boolean): Elem = {

    // todo add years from query if they exist
    val response: Elem =
      <results xmlns:icn="http://www.icn.nl/" xmlns:europeana="http://www.europeana.eu/schemas/ese/" xmlns:dc="http://purl.org/dc/elements/1.1/"
               xmlns:raw="http://delving.eu/namespaces/raw" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:ese="http://www.europeana.eu/schemas/ese/"
               xmlns:abm="http://to_be_decided/abm/" xmlns:abc="http://www.ab-c.nl/" xmlns:delving="http://www.delving.eu/schemas/"
               xmlns:drup="http://www.itin.nl/drupal" xmlns:itin="http://www.itin.nl/namespace" xmlns:tib="http://www.thuisinbrabant.nl/namespace">
        <query numFound={pagination.getNumFound.toString} firstYear="0" lastYear="0">
          <terms>
            {searchTerms}
          </terms>
          <breadCrumbs>
            {pagination.getBreadcrumbs.map(bc => <breadcrumb field={bc.field} href={minusAmp(bc.href)} value={bc.value}>
            {bc.display}
          </breadcrumb>)}
          </breadCrumbs>
        </query>
        <pagination>
          <start>
            {pagination.getStart}
          </start>
          <rows>
            {pagination.getRows}
          </rows>
          <numFound>
            {pagination.getNumFound}
          </numFound>{if (pagination.isNext) {
          <nextPage>
            {pagination.getNextPage}
          </nextPage>
            <lastPage>
              {pagination.getLastViewablePage}
            </lastPage>
        }}{if (pagination.isPrevious) <previousPage>
          {pagination.getPreviousPage}
        </previousPage>}<currentPage>
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
                <key>
                  {SearchService.localiseKey(item, language)}
                </key>
                <value>
                  {item}
                </value>
              </field>
          }}
          </fields>
          <drupal>
            {drupalLayoutMap.map(item =>
            <field>
              <key>
                {SearchService.localiseKey(item._2, language, item._1)}
              </key>
              <value>
                {item._2}
              </value>
            </field>
          )}
          </drupal>
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
          <facet name={fql.getType} isSelected={fql.facetSelected.toString} i18n={SearchService.localiseKey(fql.getType.replaceAll("_facet", "").replaceAll("_", "."), language)}>
            {fql.links.map(link =>
            <link url={minusAmp(link.url)} isSelected={link.remove.toString} value={link.value} count={link.count.toString}>
              {link.value}
              (
              {link.count.toString}
              )</link>
          )}
          </facet>
        )}
        </facets>
      </results>
    response
  }

  def renderAsJSON(authorized: Boolean): String = {
    import collection.immutable.ListMap
    import net.liftweb.json.{Extraction, JsonAST, Printer}
    implicit val formats = net.liftweb.json.DefaultFormats

    def createJsonRecord(doc: BriefDocItem): ListMap[String, Any] = {
      val recordMap = collection.mutable.ListMap[String, Any]();
      doc.getFieldValuesFiltered(false, Array())
        .sortWith((fv1, fv2) => fv1.getKey < fv2.getKey).foreach(fv => recordMap.put(fv.getKeyAsXml, SolrQueryService.encodeUrl(fv.getValueAsArray, fv.getKey, chResponse)))
      ListMap(recordMap.toSeq: _*)
    }

    def createLayoutItems: ListMap[String, Any] = {
      val recordMap = collection.mutable.ListMap[String, Any]();
      drupalLayoutMap.map(item =>
        recordMap.put(SearchService.localiseKey(item._2, item._1, language), item._2))
      ListMap(recordMap.toSeq: _*)
    }

    def createFacetList: List[ListMap[String, Any]] = {
      result.getFacetQueryLinks.map(fql =>
        ListMap("name" -> fql.getType, "isSelected" -> fql.facetSelected, "links" -> fql.links.map(link =>
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


case class FullView(fullResult: FullItemView, language: String = "en", chResponse: CHResponse) {
  //

  import xml.Elem

  private val filteredFields = Array("delving_title", "delving_description", "delving_owner", "delving_creator", "delving_snippet", "delving_fullText", "delving_fullTextObjectUrl")

  val filterKeys = List("id", "timestamp", "score")
  val uniqueKeyNames = fullResult.getFullDoc.solrDocument.getFieldNames.filterNot(_.startsWith("delving")).filterNot(filterKeys.contains(_)).toList.sortWith(_ > _)


  def renderAsXML(authorized: Boolean): Elem = {
    val response: Elem =
      <result xmlns:icn="http://www.icn.nl/" xmlns:europeana="http://www.europeana.eu/schemas/ese/" xmlns:dc="http://purl.org/dc/elements/1.1/"
              xmlns:raw="http://delving.eu/namespaces/raw" xmlns:dcterms="http://purl.org/dc/termes/" xmlns:ese="http://www.europeana.eu/schemas/ese/"
              xmlns:abm="http://to_be_decided/abm/" xmlns:abc="http://www.ab-c.nl/" xmlns:delving="http://www.delving.eu/schemas/"
              xmlns:drup="http://www.itin.nl/drupal" xmlns:itin="http://www.itin.nl/namespace" xmlns:tib="http://www.thuisinbrabant.nl/namespace">
        <layout>
          <fields>
            {uniqueKeyNames.map {
            item =>
              <field>
                <key>
                  {SearchService.localiseKey(item, language)}
                </key>
                <value>
                  {item}
                </value>
              </field>
          }}
          </fields>
        </layout>
        <item>
          <fields>
            {for (field <- fullResult.getFullDoc.getFieldValuesFiltered(false, filteredFields).sortWith((fv1, fv2) => fv1.getKey < fv2.getKey)) yield
            SolrQueryService.renderXMLFields(field, chResponse)}
          </fields>
        </item>{if (!fullResult.getRelatedItems.isEmpty)
        <relatedItems>
          {fullResult.getRelatedItems.map(item =>
          <item>
            <fields>
              {item.getFieldValuesFiltered(false, Array()).sortWith((fv1, fv2) => fv1.getKey < fv2.getKey).map(field => SolrQueryService.renderXMLFields(field, chResponse))}
            </fields>{if (item.getHighlights.isEmpty) <highlights/>
          else
            <highlights>
              {item.getHighlights.map(field => SolrQueryService.renderHighLightXMLFields(field, chResponse))}
            </highlights>}
          </item>
        )}
        </relatedItems>}
      </result>
    response
  }

  def renderAsJSON(authorized: Boolean): String = {
    import collection.immutable.ListMap
    import net.liftweb.json.{JsonAST, Extraction, Printer}
    implicit val formats = net.liftweb.json.DefaultFormats

    val recordMap = collection.mutable.ListMap[String, Any]()
    fullResult.getFullDoc.getFieldValuesFiltered(false, filteredFields)
      .sortWith((fv1, fv2) => fv1.getKey < fv2.getKey).foreach(fv => recordMap.put(fv.getKeyAsXml, fv.getValueAsArray))

    val outputJson = Printer.pretty(JsonAST.render(Extraction.decompose(
      ListMap("result" ->
        ListMap("item" -> ListMap(recordMap.toSeq: _*))
      )
    )))
    outputJson
  }
}


case class ExplainItem(label: String, options: List[String] = List(), description: String = "") {

  import xml.Elem
  import collection.immutable.ListMap

  def toXML: Elem = {
    <element>
      <label>
        {label}
      </label>{if (!options.isEmpty) <options>
      {options.map(option => <option>
        {option}
      </option>)}
    </options>}{if (!description.isEmpty) <description>
      {description}
    </description>}
    </element>
  }

  def toJson: ListMap[String, Any] = {
    if (!options.isEmpty && !description.isEmpty)
      ListMap("label" -> label, "options" -> options.toSeq, "description" -> description)
    else if (!options.isEmpty)
      ListMap("label" -> label, "options" -> options.toSeq)
    else
      ListMap("label" -> label)
  }

}

case class ExplainResponse(theme: PortalTheme) {

  import xml.Elem

  val excludeList = List("europeana_unstored", "europeana_source", "europeana_userTag", "europeana_collectionTitle")

  val paramOptions: List[ExplainItem] = List(
    ExplainItem("query", List("any string"), "Will output a summary result set. Any valid Lucene or Solr Query syntax will work."),
    ExplainItem("format", List("xml", "json", "jsonp", "simile", "similep")),
    ExplainItem("cache", List("true", "false"), "Use Services Module cache for retrieving the europeana:object"),
    ExplainItem("id", List("any valid europeana_uri identifier"), "Will output a full-view"),
    ExplainItem("idType", List("solr", "mongo", "pmh", "drupal", "datasetId", "legacy"), "//todo complete this"),
    ExplainItem("fl", List("any valid search field in a comma-separated list"), "Will only output the specified search fields"),
    ExplainItem("facet.limit", List("Any valid integer. Default is 100"), "Will limit the number of facet entries returned to integer specified."),
    ExplainItem("facetBoolType", List("AND", "OR", "Default is OR"), "Will determine how the Facet Multiselect functionality is handled within a facet. Between facets it is always AND."),
    ExplainItem("start", List("any non negative integer")),
    ExplainItem("qf", List("any valid Facet as defined in the facets block")),
    ExplainItem("hqf", List("any valid Facet as defined in the facets block"), "This link is not used for the display part of the API." +
      "It is used to send hidden constraints to the API to create custom API views"),
    ExplainItem("explain", List("all")),
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

  def renderAsXml: Elem = {

    <results>
      <api>
        <parameters>
          {paramOptions.map(param => param.toXML)}
        </parameters>
        <solr-dynamic>
          <fields>
            {solrFields.map {
            field =>
              <field xml={field.xmlFieldName} search={field.name} fieldType={field.fieldType} docs={field.docs.toString} distinct={field.distinct.toString}>
                <topTerms>
                  {field.topTerms.map {
                  term =>
                    <item count={term.freq.toString}>
                      {term.name}
                    </item>
                }}
                </topTerms>
                <histoGram>
                  {field.histogram.map {
                  term =>
                    <item count={term.freq.toString}>
                      {term.name}
                    </item>
                }}
                </histoGram>
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
            "search-fields" -> solrFields.map(facet => ExplainItem(facet.name).toJson),
            "facets" -> solrFieldsWithFacets.map(facet => ExplainItem(facet.name).toJson)))
      ))
    ))
    outputJson
  }
}

case class RecordLabel(name: String, fieldValue: String, multivalued: Boolean = false)
