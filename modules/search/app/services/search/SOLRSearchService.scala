package services.search

import core.indexing.IndexField
import exceptions.AccessKeyException
import core.indexing.IndexField._
import play.api.mvc.Results._
import play.api.http.ContentTypes._
import play.api.i18n.{ Lang, Messages }
import collection.JavaConverters._
import play.api.Logger
import play.api.mvc.PlainResult
import java.lang.String
import core.rendering._
import models.{ Visibility, MetadataCache, OrganizationConfiguration }
import xml.{ NodeSeq, PCData, PrettyPrinter, Elem }
import org.apache.solr.client.solrj.response.FacetField.Count
import org.apache.solr.client.solrj.response.FacetField
import net.liftweb.json.JsonAST._
import net.liftweb.json.{ Extraction, Printer }
import collection.immutable.ListMap
import play.templates.GenericTemplateLoader
import collection.immutable.Map
import core.HubId
import java.net.{ URLDecoder, URLEncoder }
import core.Constants._
import eu.delving.schema.SchemaVersion
import controllers.ListItem
import core.ExplainItem
import core.search._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class SOLRSearchService extends SearchService {

  val log = Logger("CultureHub")
  val prettyPrinter = new PrettyPrinter(200, 5)

  /**
   * This function parses the response for with output format needs to be rendered
   */
  override def getApiResult(queryString: Map[String, Seq[String]], host: String, hiddenQueryFilters: Seq[String] = Seq.empty)(implicit configuration: OrganizationConfiguration): PlainResult = {

    val context = SearchContext(queryString, host, hiddenQueryFilters)

    val response = try {
      if (configuration.searchService.apiWsKeyEnabled) {
        val wsKey = context.params.getValueOrElse("wskey", "unknown")
        val wsKeyProvided = !wsKey.equalsIgnoreCase("unknown")
        if ((configuration.searchService.apiWsKeyEnabled && !wsKeyProvided) ||
          (configuration.searchService.apiWsKeyEnabled && wsKeyProvided && !configuration.searchService.apiWsKeys.exists(_ == wsKey.trim))) {
          log.warn("[%s] Service Access Key %s invalid!".format(configuration.orgId, wsKey))
          throw new AccessKeyException(String.format("Access Key %s not accepted", wsKey))
        }
      }
      context.format match {
        case "json" => renderResponse(context, callback = None, representation = Representation.JSON)
        case "jsonp" => renderResponse(context, callback = Some(context.params.getValueOrElse("callback", "delvingCallback")), representation = Representation.JSON)
        case "simile" => getSimileResultResponse(context)
        case "similep" =>
          getSimileResultResponse(callback = context.params.getValueOrElse("callback", "delvingCallback"), context = context)
        case "html" if context.params.valueIsNonEmpty("id") =>
          getFullView("html", context.params.getFirst("schema"), false, context).fold(
            error => errorResponse(error, context.format),
            view => {
              val template = GenericTemplateLoader.load("tags/view.html")
              val args: java.util.Map[String, Object] = new java.util.HashMap[String, Object]()
              args.put("view", view.toViewTree)
              args.put("_view", view.toViewTree)
              args.put("lang", context.apiLanguage)
              Ok(template.render(args).replaceAll("""(?m)^\s+""", "")).as(HTML)
            }
          )

        case _ => renderResponse(context, callback = context.params.getFirst("callback"), representation = Representation.XML)
      }
    } catch {
      case t: Throwable =>
        log.error("something went wrong", t)
        errorResponse(errorMessage = t.getLocalizedMessage, format = context.format)
    }
    response
  }

  def search(user: Option[String], query: List[String], params: Map[String, Seq[String]], host: String)(implicit configuration: OrganizationConfiguration): (Seq[ListItem], SearchResult) = {

    val searchContext = SearchContext(params, host, query)
    val chQuery = SolrQueryService.createCHQuery(searchContext, user)
    val queryResponse = SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true)
    val chResponse = CHResponse(queryResponse, chQuery, configuration)
    val briefItemView = BriefItemView(chResponse)

    val items: Seq[ListItem] = briefItemView.getBriefDocs.filterNot(_.getHubId.isEmpty) map { bd =>
      ListItem(id = bd.getHubId,
        itemType = bd.getItemType,
        title = bd.getTitle,
        description = bd.getDescription,
        thumbnailUrl = bd.getThumbnailUri(220, configuration),
        userName = bd.getOrgId,
        isPrivate = bd.getVisibility.toInt == Visibility.PRIVATE.value,
        url = bd.getUri,
        mimeType = bd.getMimeType)
    }

    (items, briefItemView)

  }

  def renderResponse(context: SearchContext, authorized: Boolean = true, callback: Option[String], representation: Representation.Value)(implicit configuration: OrganizationConfiguration): PlainResult = {

    require(context.params._contains("query") || context.params._contains("id") || context.params._contains("explain"))

    val renderRelatedItems = context.params.getFirst("mlt").getOrElse("false") == "true"

    val response: Renderable = context.params match {
      case x if x._contains("explain") && x.getValueOrElse("explain", "nothing").equalsIgnoreCase("fieldValue") => FacetAutoComplete(context.params, configuration)
      case x if x._contains("explain") => ExplainResponse(context.params, configuration)
      case x if x.valueIsNonEmpty("id") => getFullView("api", x.getFirst("schema"), renderRelatedItems, context) match {
        case Right(rendered) => new Renderable {
          def toJson = None
          def asXml = Some(rendered.toXml)
          override def asJson(callback: Option[String]): Option[String] = Some({
            callback map { c =>
              "%s(%s)".format(c, rendered.toJson)
            } getOrElse {
              rendered.toJson
            }
          })
        }
        case Left(error) => return errorResponse("Unable to render full record", error, "json")
      }
      case _ =>
        val briefView = getBriefResultsFromSolr(context)
        val summary = SearchSummary(result = briefView, context = context, chResponse = briefView.chResponse)
        context.format match {
          case "kml" =>
            summary.renderAsKML(authorized, context.params)
          case "kml-a" =>
            summary.renderAsABCKML(authorized, context.params)
          case "kml-knr" =>
            summary.renderAsKNreiseKML(authorized, context.params)
          case _ =>
            summary
        }
    }

    (representation match {
      case Representation.XML =>
        response.asXml map { r =>
          Ok("<?xml version='1.0' encoding='utf-8' ?>\n" + prettyPrinter.format(r.asInstanceOf[Elem])).as(XML)
        }
      case Representation.JSON =>
        response.asJson(callback) map { r =>
          Ok(r).as(JSON)
        }
    }) getOrElse {
      errorResponse(errorMessage = s"Format ${context.format} is not available as $representation")
    }
  }

  private def getBriefResultsFromSolr(context: SearchContext)(implicit configuration: OrganizationConfiguration): BriefItemView = {
    require(context.params.valueIsNonEmpty("query"))
    val chQuery = SolrQueryService.createCHQuery(context)
    BriefItemView(CHResponse(SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery, configuration))
  }

  private def getFullView(viewName: String, schema: Option[String] = None, renderRelatedItems: Boolean, context: SearchContext)(implicit configuration: OrganizationConfiguration): Either[String, RenderedView] = {
    require(context.params._contains("id"))
    val id = context.params.getValue("id")
    val idTypeParam = context.params.getValueOrElse("idType", HUB_ID.key)
    val mltCount = context.params.getValueOrElse("mlt.count", configuration.searchService.moreLikeThis.count.toString)
    val viewType = ViewType.fromName(viewName)
    renderFullView(id, DelvingIdType(idTypeParam), viewType, Lang(context.apiLanguage), schema, renderRelatedItems, mltCount.toInt, context.queryString)
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

  def getSimileResultResponse(context: SearchContext, callback: String = "")(implicit configuration: OrganizationConfiguration): PlainResult = {
    implicit val formats = net.liftweb.json.DefaultFormats

    try {
      val output: ListMap[String, Any] = if (context.params.valueIsNonEmpty("id")) {
        // we just don't do SIMILE of full results yet
        return BadRequest
      } else {
        ListMap("items" -> getBriefResultsFromSolr(context).getBriefDocs.map(doc => renderSimileRecord(doc)))
      }

      val outputJson = Printer.pretty(render(Extraction.decompose(output)))

      if (!callback.isEmpty) {
        Ok("%s(%s)".format(callback, outputJson))
      } else
        Ok(outputJson)
    } catch {
      case ex: Exception =>
        log.error("something went wrong", ex)
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

  // ~~~ search-specific full-view rendering

  def renderFullView(id: String,
    idType: DelvingIdType,
    viewType: ViewType,
    lang: Lang,
    schema: Option[String] = None,
    renderRelatedItems: Boolean,
    relatedItemsCount: Int,
    requestParameters: Map[String, Seq[String]])(implicit configuration: OrganizationConfiguration): Either[String, RenderedView] = {

    val hasFilterByDataOwnerKey: Boolean = requestParameters.contains("dataowner") && !requestParameters.get("dataowner").get.isEmpty

    def filterByDataOwner(items: Seq[BriefDocItem], filterField: String, mltCount: Int) = {
      if (hasFilterByDataOwnerKey) {
        val filterKeys: Seq[String] = requestParameters.get("dataowner").get
        items.filter(i => filterKeys.contains(i.getFieldValue(filterField).getFirst)).take(mltCount)
      } else
        items
    }

    SolrQueryService.getSolrItemReference(URLEncoder.encode(id, "utf-8"), idType, renderRelatedItems, if (hasFilterByDataOwnerKey) relatedItemsCount + 10 else relatedItemsCount) match {
      case Some(DocItemReference(hubId, defaultSchema, publicSchemas, relatedItems, item)) =>
        val prefix = if (schema.isDefined && publicSchemas.contains(schema.get)) {
          schema.get
        } else if (schema.isDefined && !publicSchemas.contains(schema.get)) {
          val m = "Schema '%s' not available for hubId '%s'".format(schema.get, hubId)
          Logger("Search").info(m)
          return Left(m)
        } else {
          defaultSchema
        }

        idType match {
          case DelvingIdType.ITIN =>
            // TODO legacy support, to be removed on 01.06.2013
            renderItinItem(item, relatedItems)
          case DelvingIdType.INDEX_ITEM =>
            renderIndexItem(id)
          case _ =>
            renderMetadataRecord(prefix, URLDecoder.decode(hubId, "utf-8"), viewType, lang, renderRelatedItems, filterByDataOwner(relatedItems, "delving_owner", relatedItemsCount), requestParameters)
        }
      case None =>
        Left("Could not resolve identifier for hubId '%s' and idType '%s'".format(id, idType.idType))
    }
  }

  private def renderItinItem(item: Option[BriefDocItem], relatedItems: Seq[BriefDocItem]) = {

    val document = <result xmlns:delving="http://www.delving.eu/schemas/" xmlns:icn="http://www.icn.nl/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:custom="http://www.delving.eu/schemas/" xmlns:dcterms="http://purl.org/dc/termes/" xmlns:itin="http://www.itin.nl/namespace" xmlns:drup="http://www.itin.nl/drupal" xmlns:europeana="http://www.europeana.eu/schemas/ese/">
                     { item.map { i => { i.toXml() } }.getOrElse(<item/>) }
                     <relatedItems>{ relatedItems.map { ri => { ri.toXml() } } }</relatedItems>
                   </result>

    Right(new RenderedView {
      def toXml: NodeSeq = document
      def toViewTree: RenderNode = null
      def toXmlString: String = document.toString()
      def toJson: String = "JSON rendering is unsupported"
    })

  }

  private def renderIndexItem(id: String): Either[String, RenderedView] = {
    if (id.split("_").length < 3) {
      Left("Invalid hubId")
    } else {
      val hubId = HubId(id)
      val cache = MetadataCache.get(hubId.orgId, "indexApiItems", hubId.spec)
      val indexItem = cache.findOne(hubId.localId).getOrElse(return Left("Could not find IndexItem with id '%s".format(id)))
      Right(new RenderedView {
        def toXmlString: String = indexItem.xml("raw")
        def toJson: String = "JSON rendering not supported"
        def toXml: NodeSeq = scala.xml.XML.loadString(indexItem.xml("raw"))
        def toViewTree: RenderNode = null
      })
    }
  }

  private def renderMetadataRecord(prefix: String,
    hubId: String,
    viewType: ViewType,
    lang: Lang,
    renderRelatedItems: Boolean,
    relatedItems: Seq[BriefDocItem],
    parameters: Map[String, Seq[String]])(implicit configuration: OrganizationConfiguration): Either[String, RenderedView] = {

    if (hubId.split("_").length < 3) return Left("Invalid hubId " + hubId)
    val id = HubId(hubId)
    val cache = MetadataCache.get(id.orgId, id.spec, ITEM_TYPE_MDR)
    val record = cache.findOne(hubId)
    val rawRecord: Option[String] = record.flatMap(_.xml.get(prefix))
    if (rawRecord.isEmpty) {
      log.info("Could not find cached record in mongo with format %s for hubId %s".format(prefix, hubId))
      Left("Could not find full record with hubId '%s' for format '%s'".format(hubId, prefix))
    } else {

      // handle legacy formats
      val legacyApiFormats = List("tib", "abm", "ese", "abc")
      val legacyHtmlFormats = List("abm", "ese", "abc")
      val viewDefinitionFormatName = if (viewType == ViewType.API) {
        if (legacyApiFormats.contains(prefix)) "legacy" else prefix
      } else {
        if (legacyHtmlFormats.contains(prefix)) "legacy" else prefix
      }

      val schemaVersion = record.get.schemaVersions(prefix)

      RecordRenderer.renderMetadataRecord(hubId, rawRecord.get, new SchemaVersion(prefix, schemaVersion), viewDefinitionFormatName, viewType, lang, renderRelatedItems, relatedItems.map(_.toXml()), Seq.empty, parameters)
    }
  }

}

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/17/11 2:25 PM
 */
object SOLRSearchService {

  def localiseKey(metadataField: String, language: String = "en", defaultLabel: String = "unknown"): String = {
    val localizedName = Messages("metadata." + metadataField.replaceAll("_", "."))(Lang(language))
    if (localizedName != null && !defaultLabel.startsWith("#") && !localizedName.startsWith("metadata.")) localizedName else defaultLabel
  }

}

case class SearchContext(queryString: Map[String, Seq[String]], host: String, hiddenQueryFilters: Seq[String]) {
  val params = Params(queryString)
  val format = params.getValueOrElse("format", "default")
  val apiLanguage = params.getValueOrElse("lang", "en")
}

case class RecordLabel(name: String, fieldValue: String, multivalued: Boolean = false)

case class SearchSummary(result: BriefItemView, context: SearchContext, chResponse: CHResponse) extends Renderable {

  private val pagination = result.getPagination
  private val searchTerms = pagination.getPresentationQuery.getUserSubmittedQuery
  private val filteredFields = Seq("delving_snippet", IndexField.FULL_TEXT.key, "delving_SNIPPET")

  val baseUrl = context.host

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
      SOLRSearchService.localiseKey("type.%ss".format(value.toLowerCase), context.apiLanguage)
    else
      value
  }

  def renderAsABCKML(authorized: Boolean, params: Params): Renderable = new Renderable {
    def toJson = None

    def asXml = Some({

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
        renderStrong("Vindplaats", "icn_location", output)
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
    })
  }

  def renderAsKML(authorized: Boolean, params: Params): Renderable = new Renderable {

    def toJson = None

    def asXml = Some({

      // todo remove this hack later
      val sfield = params.getValueOrElse("sfield", GEOHASH.key) match {
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
                          <SimpleField name={ item } type="string">{ SOLRSearchService.localiseKey(item, context.apiLanguage) }</SimpleField>
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
                              { item.toKmFields(filteredFields = filteredFields, language = context.apiLanguage).map(field => field) }
                            </SchemaData>
                          } else {
                            List(<Data name='delving:linkHome'><value>{ "http://%s/%s/%s/%s".format(context.host, item.getOrgId, item.getSpec, item.getRecordId) }</value></Data>) :::
                              item.toKmFields(filteredFields = filteredFields, language = context.apiLanguage, simpleData = useSchema).map(field => field)
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
    })
  }

  def renderAsKNreiseKML(authorized: Boolean, params: Params): Renderable = new Renderable {
    def toJson = None

    def asXml = Some({
      // TODO remove this hack later
      val sfield = params.getValueOrElse("sfield", GEOHASH.key) match {
        case "abm_geo_geohash" => "abm_geo"
        case _ => GEOHASH.key
      }

      val knReiseFilterFields = Seq("delving_snippet", IndexField.FULL_TEXT.key, "delving_SNIPPET", "delving_description",
        "delving_currentSchema", "delving_recordType", "delving_provider", "delving_pmhId", "delving_allSchemas",
        "delving_geohash", "delving_schema", "delving_creator", "delving_landing", "delving_collection",
        "delving_hasDigitalObject", "delving_hasGeoHash", "delving_hasLandingPage", "delving_landingPage", "delving_orgId",
        "delving_spec", "delving_thumbnail", "delving_title", "delving_visibility", "delving_hubId", "delving_owner")

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
                          <SimpleField name={ item } type="string">{ SOLRSearchService.localiseKey(item, context.apiLanguage) }</SimpleField>
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
                              { item.toKmFields(filteredFields = filteredFields, language = context.apiLanguage).map(field => field) }
                            </SchemaData>
                          } else {
                            List(<Data name='delving:linkHome'><value>{ "http://%s/%s/%s/%s".format(context.host, item.getOrgId, item.getSpec, item.getRecordId) }</value></Data>) :::
                              item.toKmFields(filteredFields = knReiseFilterFields, language = context.apiLanguage, simpleData = useSchema).map(field => field)
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

    })
  }

  def asXml = Some({

    // todo add years from query if they exist
    val response: Elem =
      <results xmlns:delving="http://www.delving.eu/schemas/" xmlns:aff="http://schemas.delving.eu/aff/" xmlns:icn="http://www.icn.nl/" xmlns:europeana="http://www.europeana.eu/schemas/ese/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:raw="http://delving.eu/namespaces/raw" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:ese="http://www.europeana.eu/schemas/ese/" xmlns:abm="http://schemas.delving.eu/abm/" xmlns:abc="http://www.ab-c.nl/" xmlns:drup="http://www.itin.nl/drupal" xmlns:itin="http://www.itin.nl/namespace" xmlns:tib="http://www.thuisinbrabant.nl/namespace" xmlns:musip="http://www.musip.nl/" xmlns:custom="http://www.delving.eu/namespaces/custom">
        <query numFound={ pagination.getNumFound.toString }>
          <terms>{ searchTerms }</terms>
          <breadCrumbs>
            {
              pagination.getBreadcrumbs.map(bc =>
                <breadcrumb field={ bc.field } href={ minusAmp(bc.href) } value={ bc.value } i18n={ SOLRSearchService.localiseKey(SolrBindingService.stripDynamicFieldLabels(bc.field), context.apiLanguage) }>{ bc.display }</breadcrumb>)
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
                    <i18n>{ SOLRSearchService.localiseKey(item, context.apiLanguage) }</i18n>
                  </field>
              }
            }
          </fields>
        </layout>
        <items>{ briefDocs.map(item => item.toXml(filteredFields)) }</items>
        <facets>
          {
            result.getFacetQueryLinks.map(fql =>
              <facet name={ fql.getType } isSelected={ fql.facetSelected.toString } i18n={ SOLRSearchService.localiseKey(SolrBindingService.stripDynamicFieldLabels(fql.getType), context.apiLanguage) } missingDocs={ fql.getMissingValueCount.toString }>
                {
                  fql.links.map(link => {
                    val i18nValue = translateFacetValue(fql.getType, link.getValue)
                    <link url={ minusAmp(link.url) } isSelected={ link.remove.toString } value={ i18nValue } count={ link.getCount.toString }>{ i18nValue } ({ link.getCount.toString })</link>
                  })
                }
              </facet>
            )
          }
        </facets>
      </results>
    response
  })

  def toJson = Some({
    def createJsonRecord(doc: BriefDocItem): ListMap[String, Any] = {
      val recordMap = collection.mutable.ListMap[String, Any]()
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
        ListMap("name" -> fql.getType, "isSelected" -> fql.facetSelected, "i18n" -> SOLRSearchService.localiseKey(fql.getType.replaceAll("_facet", "").replaceAll("_", "."), context.apiLanguage), "links" -> fql.links.map(link =>
          ListMap("url" -> minusAmp(link.url), "isSelected" -> link.remove, "value" -> link.getValue, "count" -> link.getCount, "displayString" -> "%s (%s)".format(link.getValue, link.getCount))))
      ).toList
    }

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
          ListMap[String, Any]("layout" -> uniqueKeyNames.map(item => ListMap("name" -> item, "i18n" -> SOLRSearchService.localiseKey(item, context.apiLanguage)))),
        "items" ->
          result.getBriefDocs.map(doc => createJsonRecord(doc)).toList,
        "facets" -> createFacetList
      )
    )
  })
}

case class FacetAutoComplete(params: Params, configuration: OrganizationConfiguration) extends Renderable {
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

  def asXml = Some({
    <results>
      {
        autocomplete.map(item =>
          <item count={ item.getCount.toString }>{ item.getName }</item>
        )
      }
    </results>
  })

  def toJson = Some({
    ListMap("results" ->
      autocomplete.map(item => ListMap("value" -> item.getName, "count" -> item.getCount)
      ))
  })

}

case class ExplainResponse(params: Params, configuration: OrganizationConfiguration) extends Renderable {

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

  def asXml = Some({

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
  })

  def toJson = Some({
    ListMap("results" ->
      ListMap("api" ->
        ListMap(
          "parameters" -> paramOptions.map(param => param.toJson).toIterable,
          "search-fields" -> solrFields.map(facet => ListMap("search" -> facet.name, "xml" -> facet.xmlFieldName, "distinct" -> facet.distinct.toString,
            "docs" -> facet.docs.toString, "fieldType" -> facet.fieldType)),
          "facets" -> solrFieldsWithFacets.map(facet => ListMap("search" -> facet.name, "xml" -> facet.xmlFieldName, "distinct" -> facet.distinct.toString,
            "docs" -> facet.docs.toString, "fieldType" -> facet.fieldType))
        ))
    )
  })
}

object Representation extends Enumeration {
  val XML = Value
  val JSON = Value
}

trait Renderable {
  def toJson: Option[ListMap[String, AnyRef]]
  def asXml: Option[NodeSeq]
  def asJson(callback: Option[String] = None): Option[String] = {
    implicit val formats = net.liftweb.json.DefaultFormats
    toJson map { json =>
      val rendered = Printer.pretty(render(Extraction.decompose(json)))
      callback map { c =>
        "%s(%s)".format(c, rendered)
      } getOrElse {
        rendered
      }
    }
  }
}