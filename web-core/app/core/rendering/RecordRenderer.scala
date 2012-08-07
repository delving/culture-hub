package core.rendering

import core.Constants._
import core.search.{DelvingIdType, BriefDocItem, DocItemReference, SolrQueryService}
import models.{RecordDefinition, MetadataCache, DomainConfiguration}
import java.net.{URLDecoder, URLEncoder}
import play.api.Logger
import xml._
import play.api.i18n.Lang
import scala.Left
import scala.Some
import scala.Right

/**
 * Renders a single record, with or without related items. Makes use of the search engine to retrieve related records and IDs.
 *
 * TODO simplify and consolidate this mess when we've got some time.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object RecordRenderer {

  val log = Logger("CultureHub")

  /**
   * Gets the full view of a record, for access via the search API.
   * This is complex because we have to cater for legacy deployments with special idTypes
   */
  def getRenderedFullView(id: String,
                          idType: DelvingIdType,
                          language: String,
                          schema: Option[String] = None,
                          renderRelatedItems: Boolean)(implicit configuration: DomainConfiguration): Either[String, RenderedView] = {


    SolrQueryService.getSolrItemReference(URLEncoder.encode(id, "utf-8"), idType, renderRelatedItems) match {
      case Some(DocItemReference(hubId, defaultSchema, publicSchemas, relatedItems)) =>
        val prefix = if(schema.isDefined && publicSchemas.contains(schema.get)) {
          schema.get
        } else if(schema.isDefined && !publicSchemas.contains(schema.get)) {
          val m = "Schema '%s' not available for hubId '%s'".format(schema.get, hubId)
          Logger("Search").info(m)
          return Left(m)
        } else {
          defaultSchema
        }

        if(idType == "indexItem") {
          renderIndexItem(id)
        } else {
          renderMetadataRecord(prefix, URLDecoder.decode(hubId, "utf-8"), ViewType.API, language, renderRelatedItems, relatedItems)
        }
      case None =>
        Left("Could not resolve identifier for hubId '%s' and idType '%s'".format(id, idType.idType))
    }
  }

  private def renderIndexItem(id: String): Either[String, RenderedView] = {
    if(id.split("_").length < 3) {
      Left("Invalid hubId")
    } else {
      val HubId(orgId, itemType, itemId) = id
      val cache = MetadataCache.get(orgId, "indexApiItems", itemType)
      val indexItem = cache.findOne(itemId).getOrElse(return Left("Could not find IndexItem with id '%s".format(id)))
      Right(new RenderedView {
        def toXmlString: String = indexItem.getRawXmlString
        def toJson: String = "JSON rendering not supported"
        def toXml: NodeSeq = scala.xml.XML.loadString(indexItem.getRawXmlString)
        def toViewTree: RenderNode = null
      })
    }
  }

  private def renderMetadataRecord(prefix: String, hubId: String, viewType: ViewType, language: String, renderRelatedItems: Boolean, relatedItems: Seq[BriefDocItem])(implicit configuration: DomainConfiguration): Either[String, RenderedView] = {
    if(hubId.split("_").length < 3) return Left("Invalid hubId " + hubId)
    val HubId(orgId, collection, itemId) = hubId
    val cache = MetadataCache.get(orgId, collection, ITEM_TYPE_MDR)
    val rawRecord: Option[String] = cache.findOne(hubId).flatMap(_.xml.get(prefix))
    if (rawRecord.isEmpty) {
      Logger("Search").info("Could not find cached record in mongo with format %s for hubId %s".format(prefix, hubId))
      Left("Could not find full record with hubId '%s' for format '%s'".format(hubId, prefix))
    } else {

      // handle legacy formats
      val legacyApiFormats = List("tib", "abm", "ese", "abc")
      val legacyHtmlFormats = List("abm", "ese", "abc")
      val viewDefinitionFormatName = if (viewType == ViewType.API) {
        if(legacyApiFormats.contains(prefix)) "legacy" else prefix
      } else {
        if(legacyHtmlFormats.contains(prefix)) "legacy" else prefix
      }
      renderMetadataRecord(hubId, rawRecord.get, prefix, viewDefinitionFormatName, viewType, language, renderRelatedItems, relatedItems)
    }
  }

  /**
   * Renders a metadata record
   *
   * @param hubId the hubId
   * @param rawRecord the raw record (XML string)
   * @param schemaPrefix prefix of the schema the record is in
   * @param viewDefinitionFormatName prefix of the view rendering schema. in principle the same as the one of the record, but there may be exceptions (e.g. "legacy")
   * @param viewType viewType (API / HTML)
   * @param language rendering languages
   * @param renderRelatedItems whether to render related items
   * @param relatedItems the related items
   * @param configuration DomainConfiguration
   * @return a rendered view if successful, or an error message
   */
  def renderMetadataRecord(hubId: String,
                           rawRecord: String,
                           schemaPrefix: String,
                           viewDefinitionFormatName: String,
                           viewType: ViewType,
                           language: String,
                           renderRelatedItems: Boolean,
                           relatedItems: Seq[BriefDocItem],
                           parameters: Map[String, String] = Map.empty)(implicit configuration: DomainConfiguration): Either[String, RenderedView]  = {

      // let's do some rendering
      RecordDefinition.getRecordDefinition(schemaPrefix) match {
        case Some(definition) =>
          val viewRenderer = ViewRenderer.fromDefinition(viewDefinitionFormatName, viewType.name, configuration)
          if (viewRenderer.isEmpty) {
            log.warn("Tried rendering full record with id '%s' for non-existing view type '%s'".format(hubId, viewType.name))
            Left("Could not render full record with hubId '%s' for view type '%s': view type does not exist".format(hubId, viewType.name))
          } else {
            try {
              val cleanRawRecord = if(renderRelatedItems) {
                // mix the related items to the record coming from mongo
                // TODO only pass in delving:* fields
                val record = scala.xml.XML.loadString(rawRecord)
                val relatedItemsXml = <relatedItems>{relatedItems.map(_.toXml(filteredFields = Seq("delving_title", "delving_thumbnail", "delving_hubId"), include = true))}</relatedItems>
                var mergedRecord: Elem = addChild(record, relatedItemsXml).get // we know what we're doing here

                // prepend the delving namespace if it ain't there
                // and yes this check is ugly but scala's XML <-> namespace support ain't pretty to say the least
                if(!rawRecord.contains("xmlns:delving")) {
                  mergedRecord = mergedRecord % new UnprefixedAttribute("xmlns:delving", "http://www.delving.eu/schemas/", Null)
                }

                mergedRecord.toString().replaceFirst("<\\?xml.*?>", "")
              } else {
                rawRecord.replaceFirst("<\\?xml.*?>", "")
              }
              log.debug(cleanRawRecord)

              // TODO see what to do with roles
              val rendered: RenderedView = viewRenderer.get.renderRecord(cleanRawRecord, List.empty, definition.getNamespaces + ("delving" -> "http://www.delving.eu/schemas/"), Lang(language), parameters)
              Right(rendered)
            } catch {
              case t: Throwable =>
                log.error("Exception while rendering view %s for record %s".format(schemaPrefix, hubId), t)
                Left("Error while rendering view '%s' for record with hubId '%s'".format(schemaPrefix, hubId))
            }
          }
        case None =>
          val m = "Error while rendering view '%s' for record with hubId '%s': could not find record definition with prefix '%s'".format(schemaPrefix, hubId, schemaPrefix)
          log.error(m)
          Left(m)
      }
  }

  private def addChild(n: Node, newChild: Node): Option[Elem] = n match {
    case Elem(prefix, label, attribs, scope, child @ _*) => Some(Elem(prefix, label, attribs, scope, child ++ newChild : _*))
    case _ =>
      Logger("CultureHub").error("Can only add children to elements!")
      None
  }

}

case class ViewType(name: String)

object ViewType {
  val API = ViewType("api")
  val HTML = ViewType("html")
}