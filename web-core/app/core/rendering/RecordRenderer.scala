package core.rendering

import models.{ Role, RecordDefinition, OrganizationConfiguration }
import play.api.Logger
import xml._
import play.api.i18n.Lang
import eu.delving.schema.SchemaVersion
import scala.xml.Elem

/**
 * Renders a single record, with or without related items. Makes use of the search engine to retrieve related records and IDs.
 *
 * TODO simplify and consolidate this mess when we've got some time.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object RecordRenderer {

  val log = Logger("CultureHub")

  def canRender(schemaVersion: SchemaVersion, viewType: ViewType)(implicit configuration: OrganizationConfiguration): Boolean =
    ViewRenderer.canRender(schemaVersion.getPrefix, viewType)

  /**
   * Renders a metadata record
   *
   * @param hubId the hubId
   * @param recordXml the raw record (XML string)
   * @param schema the schema being rendered
   * @param viewDefinitionFormatName prefix of the view rendering schema. in principle the same as the one of the record, but there may be exceptions (e.g. "legacy")
   * @param viewType viewType (API / HTML)
   * @param lang rendering languages
   * @param renderRelatedItems whether to render related items
   * @param relatedItems the related items
   * @param roles the Roles available for the user requesting rendering
   * @param parameters parameters that can be used during rendering
   * @param configuration OrganizationConfiguration
   * @return a rendered view if successful, or an error message
   */
  def renderMetadataRecord(hubId: String,
    recordXml: String,
    schema: SchemaVersion,
    viewDefinitionFormatName: String,
    viewType: ViewType,
    lang: Lang,
    renderRelatedItems: Boolean = false,
    relatedItems: Seq[NodeSeq] = Seq.empty,
    roles: Seq[Role] = Seq.empty,
    parameters: Map[String, Seq[String]] = Map.empty,
    availableSchemas: List[String] = List.empty)(implicit configuration: OrganizationConfiguration): Either[String, RenderedView] = {

    // let's do some rendering
    RecordDefinition.getRecordDefinition(schema) match {
      case Some(definition) =>
        if (!ViewRenderer.canRender(viewDefinitionFormatName, viewType)) {
          log.warn("Tried rendering full record with id '%s' for non-existing view type '%s'".format(hubId, viewType.name))
          Left("Could not render full record with hubId '%s' for view type '%s': view type does not exist".format(hubId, viewType.name))
        } else {
          try {

            val defaultNamespaces = Seq(
              "delving" -> "http://schemas.delving.eu/",
              "xml" -> "http://www.w3.org/XML/1998/namespace",
              "xsi" -> "http://www.w3.org/2001/XMLSchema-instance"
            )

            val relatedItemsNamespaces: Seq[(String, String)] = {
              val namespaces = relatedItems.flatMap { item =>
                (item \\ "fields").flatMap { field =>
                  field.child.map { field =>
                    field.prefix
                  }
                }.distinct
                  .filterNot(_.trim.isEmpty)
                  .map { prefix =>
                    prefix -> RecordDefinition.getNamespaceURI(prefix)
                  }
              }

              val (resolved, missing) = namespaces.partition(_._2.isDefined)

              if (!missing.isEmpty) {
                log.warn("While rendering full view for item %s: following prefixes for related items are unknown: %s".format(
                  hubId, missing.map(_._1).mkString(", ")
                ))
              }

              resolved.map(r => r._1 -> r._2.get)
            }

            val cleanRawRecord = {
              val record = scala.xml.XML.loadString(recordXml)
              var mutableRecord: Elem = if (renderRelatedItems) {
                // mix the related items to the cached record coming from mongo
                val relatedItemsXml = <relatedItems> { relatedItems } </relatedItems>
                addChild(record, relatedItemsXml).get // we know what we're doing here
              } else {
                record
              }

              val additionalNamespaces: Map[String, String] = definition.getNamespaces ++ relatedItemsNamespaces ++ defaultNamespaces

              additionalNamespaces.foreach { ns =>

                // prepend missing namespaces to the declaration if they ain't there
                // and yes this check is ugly but Scala's XML <-> namespace support ain't pretty to say the least
                if (!recordXml.contains("xmlns:" + ns._1)) {
                  mutableRecord = mutableRecord % new UnprefixedAttribute("xmlns:" + ns._1, ns._2, Null)
                }
              }

              // apply transformer chain
              // TODO plug-in plugins into the chain
              val baseRecord: NodeSeq = mutableRecord

              val cleanRecord = DefaultRecordTransformers.transformers.foldLeft(baseRecord) { (record: NodeSeq, transformer) =>
                transformer.transformRecord(record, RenderingContext(parameters))
              }

              cleanRecord.toString().replaceFirst("<\\?xml.*?>", "")
            }

            log.debug(cleanRawRecord)

            val allNamespaces = definition.getNamespaces ++ relatedItemsNamespaces ++ defaultNamespaces
            val viewRenderer = ViewRenderer.fromDefinition(viewDefinitionFormatName, viewType, allNamespaces)
            val rendered: RenderedView = viewRenderer.get.renderRecord(cleanRawRecord, roles, lang, parameters)
            Right(rendered)
          } catch {
            case t: Throwable =>
              log.error("Exception while rendering view %s for record %s".format(schema, hubId), t)
              Left("Error while rendering view '%s' for record with hubId '%s'".format(schema, hubId))
          }
        }
      case None =>
        val m = "Error while rendering view '%s' for record with hubId '%s': could not find record definition with prefix '%s'".format(schema, hubId, schema)
        log.error(m)
        Left(m)
    }
  }

  private def addChild(n: Node, newChild: Node): Option[Elem] = n match {
    case Elem(prefix, label, attribs, scope, child @ _*) => Some(Elem(prefix, label, attribs, scope, true, child ++ newChild: _*))
    case _ =>
      Logger("CultureHub").error("Can only add children to elements!")
      None
  }

}

case class ViewType(name: String)

object ViewType {
  val API = ViewType("api")
  val HTML = ViewType("html")

  def fromName(name: String) = if (name == "html") HTML else API
}