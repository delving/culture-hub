package core.processing

import akka.actor.{ PoisonPill, Actor }
import core.{ SystemField, HubId }
import eu.delving.schema.SchemaVersion
import core.mapping.MappingService
import models.{ MetadataCache, MetadataItem }
import eu.delving.MappingResult
import scala.collection.JavaConverters._
import java.util.concurrent.atomic.AtomicBoolean

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class MappedRecordCacher(processingContext: ProcessingContext, processingInterrupted: AtomicBoolean) extends Actor {

  type MultiMap = Map[String, List[String]]

  private val cache = MetadataCache.get(
    processingContext.collection.getOwner,
    processingContext.collection.spec,
    processingContext.collection.itemType)

  def receive = {

    case CacheMappedRecord(index, hubId, mappedRecords) =>

      if (processingInterrupted.get()) {
        self ! PoisonPill
      } else {

        val allSystemFields: Option[MultiMap] = processingContext.renderingSchema.flatMap { s =>
          mappedRecords.get(s).map { r: MappingResult =>
            getSystemFields(r)
          }
        }

        val serializedRecords: Map[String, Option[String]] = mappedRecords.map { r =>
          try {
            val serialized = MappingService.nodeTreeToXmlString(r._2.rootAugmented(), r._1.getPrefix != "raw")
            (r._1.getPrefix -> Some(serialized))
          } catch {
            case t: Throwable => {
              //            log.error(
              //              """While attempting to serialize the following output document:
              //                |
              //                |%s
              //                |
              //              """.stripMargin.format(r._2.root()), t)
              //            throw t
              sender ! MappedRecordCachingFailure(index, hubId, r._1, r._2, t)
              (r._1.getPrefix -> None)
            }
          }
        }.toMap

        if (!serializedRecords.values.exists(_ == None)) {

          val mappingResultSchemaVersions: Map[String, String] = serializedRecords.keys.
            flatMap(schemaPrefix => processingContext.targetSchemas.find(_.prefix == schemaPrefix)).
            map(processingSchema => (processingSchema.definition.prefix -> processingSchema.definition.schemaVersion)).
            toMap

          val cachedRecord = MetadataItem(
            collection = processingContext.collection.spec,
            itemType = processingContext.collection.itemType.itemType,
            itemId = hubId.toString,
            xml = serializedRecords.map(r => (r._1 -> r._2.get)),
            schemaVersions = mappingResultSchemaVersions,
            systemFields = allSystemFields.getOrElse(Map.empty),
            index = index
          )
          cache.saveOrUpdate(cachedRecord)
        }
      }
  }

  private def getCopyFields(mappingResult: MappingResult): MultiMap = {
    mappingResult.copyFields().asScala.map(f => (f._1.replaceAll(":", "_") -> f._2.asScala.toList)).toMap[String, List[String]]
  }

  private def getSystemFields(mappingResult: MappingResult): MultiMap = {
    getCopyFields(mappingResult).filter(f => SystemField.isValid(f._1))
  }

  private def getOtherFields(mappingResult: MappingResult): MultiMap = {
    getCopyFields(mappingResult).filterNot(f => SystemField.isValid(f._1))
  }

}

case class CacheMappedRecord(index: Int, hubId: HubId, records: Map[SchemaVersion, MappingResult])

case class MappedRecordCachingFailure(index: Int, hubId: HubId, schema: SchemaVersion, result: MappingResult, throwable: Throwable)
