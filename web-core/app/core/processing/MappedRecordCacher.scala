package core.processing

import akka.actor.{ PoisonPill, Actor }
import core.{ SystemField, HubId }
import eu.delving.schema.SchemaVersion
import core.mapping.MappingService
import models.{ MetadataCache, MetadataItem }
import eu.delving.MappingResult
import scala.collection.JavaConverters._
import java.util.concurrent.atomic.AtomicBoolean
import com.yammer.metrics.scala.Instrumented
import play.api.Logger
import java.util.concurrent.TimeUnit

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class MappedRecordCacher(processingContext: ProcessingContext, processingInterrupted: AtomicBoolean) extends Actor with Instrumented {

  type MultiMap = Map[String, List[String]]

  val counter = metrics.counter("recordCacherCounter")
  val meter = metrics.meter("recordCacherMeter", "cached records", unit = TimeUnit.SECONDS)
  val serializationTimer = metrics.timer(processingContext.collection.getOwner + ".recordCacherSerializationTimer", durationUnit = TimeUnit.MILLISECONDS, rateUnit = TimeUnit.SECONDS)
  val cachingTimer = metrics.timer(processingContext.collection.getOwner + ".recordCacherCachingTimer", durationUnit = TimeUnit.MILLISECONDS, rateUnit = TimeUnit.SECONDS)

  val log = Logger("CultureHub")

  private val cache = MetadataCache.get(
    processingContext.collection.getOwner,
    processingContext.collection.spec,
    processingContext.collection.itemType)

  override def postStop() {
    counter.clear()
    serializationTimer.clear()
  }

  def receive = {

    case CacheMappedRecord(index, hubId, mappedRecords) =>

      if (processingInterrupted.get()) {
        self ! PoisonPill
      } else {
        meter.mark()

        val allSystemFields: Option[MultiMap] = processingContext.renderingSchema.flatMap { s =>
          mappedRecords.get(s).map { r: MappingResult =>
            getSystemFields(r)
          }
        }

        val serializedRecords: Map[String, Option[String]] = serializationTimer.time {

          mappedRecords.map { r =>
            try {
              val serialized = MappingService.nodeTreeToXmlString(r._2.rootAugmented(), r._1.getPrefix != "raw")
              (r._1.getPrefix -> Some(serialized))
            } catch {
              case t: Throwable => {
                if (log.isDebugEnabled) {
                  log.debug("Problem during record serialization for caching", t)
                }
                sender ! MappedRecordCachingFailure(index, hubId, t, Some(r._1), Some(r._2))
                (r._1.getPrefix -> None)
              }
            }
          }.toMap

        }

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
          try {
            cachingTimer.time {
              cache.saveOrUpdate(cachedRecord)
            }
            sender ! MappedRecordCachingSuccess
            counter += 1

            if (log.isDebugEnabled) {
              if (counter.count % 1000 == 0) {
                log.debug(
                  s"""Processing metrics from MappedRecordCacher:
                    |- cached records: ${counter.count}
                    |- caching rate: ${meter.meanRate} records / second
                    |- serialization time: ${serializationTimer.mean} ms
                    |- caching time: ${cachingTimer.mean} ms
                  """.stripMargin)
              }
            }

          } catch {
            case t: Throwable =>
              if (log.isDebugEnabled) {
                log.debug("Problem during record caching", t)
              }
              sender ! MappedRecordCachingFailure(index, hubId, t)
          }

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

case object MappedRecordCachingSuccess
case class MappedRecordCachingFailure(index: Int, hubId: HubId, cause: Throwable, schema: Option[SchemaVersion] = None, result: Option[MappingResult] = None)
