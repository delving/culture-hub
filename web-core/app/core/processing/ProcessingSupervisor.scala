package core.processing

import akka.actor.{ Props, Actor }
import core.{ IndexingService, DomainServiceLocator, HubModule, HubId }
import collection.mutable.ArrayBuffer
import eu.delving.schema.SchemaVersion
import models.OrganizationConfiguration
import play.api.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import akka.routing.RoundRobinRouter
import concurrent.duration.Duration
import org.w3c.dom.Node

/**
 * Supervises a processing run.
 *
 * This actor spawns 3 subsystems: mapping, caching, and indexing.
 * It halts upon error from any of the systems (except indexing, which is "fire and forget")
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class ProcessingSupervisor(
    totalSourceRecords: Int,
    updateCount: Long => Unit,
    indexOne: (HubId, SchemaVersion, MultiMap, Node) => Option[Throwable],
    interrupted: => Boolean,
    onProcessingDone: ProcessingContext => Unit,
    whenDone: () => Unit,
    onError: Throwable => Unit,
    processingContext: ProcessingContext,
    configuration: OrganizationConfiguration) extends Actor {

  // TODO pull this into the creator of this actor via onInterrupted
  private val indexingServiceLocator = HubModule.inject[DomainServiceLocator[IndexingService]](name = None)

  private val log = Logger("CultureHub")

  private val processingInterrupted = new AtomicBoolean(false)

  private val numInstances = {
    val round = (math.round(Runtime.getRuntime.availableProcessors() * configuration.processingService.mappingCpuProportion)).toInt
    if (round == 0) 1 else round
  }

  private val halfNumInstances = {
    val round = math.round(numInstances / 2)
    if (round == 0) 1 else round
  }

  private val recordMapper = context.actorOf(Props(new RecordMapper(processingContext, processingInterrupted)).withRouter(
    RoundRobinRouter(nrOfInstances = numInstances))
  )
  private val recordCacher = context.actorOf(Props(new MappedRecordCacher(processingContext, processingInterrupted)).withRouter(
    RoundRobinRouter(nrOfInstances = halfNumInstances)
  ))
  private val recordIndexer = context.actorOf(Props(new RecordIndexer(processingContext, indexOne, processingInterrupted, configuration)).withRouter(
    RoundRobinRouter(nrOfInstances = halfNumInstances)
  ))

  private var numSourceRecords: Int = 0
  private var numMappingResults: Int = 0
  private var numCachingResults: Int = 0
  private var numCachingFailures: Int = 0
  private val mappingFailures = new ArrayBuffer[(Int, HubId, String, Throwable)]

  private val modulo = math.round(totalSourceRecords / 100)

  def receive = {

    case ProcessRecord(index, hubId, sourceRecord, targetSchemas) =>
      numSourceRecords = numSourceRecords + 1
      recordMapper ! MapRecord(index, hubId, sourceRecord, targetSchemas)

    case GetQueueSize =>
      sender ! (numSourceRecords - numMappingResults)

    case RecordMappingResult(index, hubId, results) =>
      numMappingResults = numMappingResults + 1

      val tick = numMappingResults % (if (modulo == 0) 100 else modulo) == 0

      if (tick && interrupted) {
        log.info("Processing of collection %s of organization %s interrupted after %s seconds".format(
          processingContext.collection.spec,
          processingContext.collection.getOwner,
          Duration(System.currentTimeMillis() - processingContext.startProcessing.toDate.getTime, TimeUnit.MILLISECONDS).toSeconds
        ))
        handleInterrupt()
      } else {
        recordCacher ! CacheMappedRecord(index, hubId, results)

        processingContext.indexingSchema.foreach { indexingSchema =>
          results.get(indexingSchema).foreach { result =>

            // TODO once the record definitions are cleaned up, clean this up.
            val fieldsToIndex = result.getFields ++ result.getSearchFields ++ result.getOtherFields ++ result.getSystemFields

            recordIndexer ! IndexRecord(hubId, indexingSchema, fieldsToIndex, result.getDocument)
          }
        }

      }

    case f @ RecordMappingFailure(index, hubId, sourceRecord, throwable) =>
      mappingFailures += ((index, hubId, sourceRecord, throwable))
      handleError(f)

    case MappedRecordCachingSuccess =>
      numCachingResults += 1

      val tick = numCachingResults % (if (modulo == 0) 100 else modulo) == 0

      if (tick) {
        updateCount(numCachingResults)
      }

      if (numCachingResults % 2000 == 0) {
        log.info(
          s"${processingContext.collection.getOwner}:${processingContext.collection.spec}: " +
            s"processed $numCachingResults of $totalSourceRecords records, for schemas '${processingContext.targetSchemasString}' (with $numInstances instances)"
        )
      }

      if (log.isDebugEnabled) {
        if (numCachingResults % 5000 == 0) {
          log.debug(
            s"""Processing metrics from ProcessingSupervisor:
              |- source records: $numSourceRecords
              |- mapped records: $numMappingResults
              |- cached records: $numCachingResults
              |- caching failures: $numCachingFailures
            """.stripMargin)
        }
      }

      if ((numCachingResults + numCachingFailures) == totalSourceRecords) {
        self ! ProcessingDone
      }

    case MappedRecordCachingFailure =>
      numCachingFailures += 1

    case ProcessingDone =>
      if (interrupted) {
        handleInterrupt()
      } else {
        log.info("%s: processed %s of %s records, for schemas '%s'".format(
          processingContext.collection.spec, numMappingResults, totalSourceRecords, processingContext.targetSchemasString)
        )
        updateCount(numMappingResults)
        onProcessingDone(processingContext)
        log.info("Processing of collection %s of organization %s finished, took %s seconds".format(
          processingContext.collection.spec,
          processingContext.collection.getOwner,
          Duration(System.currentTimeMillis() - processingContext.startProcessing.toDate.getTime, TimeUnit.MILLISECONDS).toSeconds)
        )
        whenDone()
      }

  }

  private def handleError(e: Any) {
    e match {
      case RecordMappingFailure(index, hubId, sourceRecord, throwable) =>
        log.error(
          """
            |Error during mapping of record at index %s, hubId %s: %s
            |
            |Source record:
            |--------------
            |
            |%s
            |
          """.stripMargin.format(index, hubId, throwable.getMessage, sourceRecord), throwable)
        onError(throwable)
      case MappedRecordCachingFailure(index, hubId, throwable, schema, result) if (schema != None && result != None) => {
        log.error(
          """
            |Error during caching of record at index %s for schema %s, hubId %s: %s
            |
            |Mapping result:
            |---------------
            |
            |%s
            |
          """.stripMargin.format(index, schema.getOrElse(""), hubId, throwable.getMessage, result.getOrElse("")), throwable)
        onError(throwable)
      }
      case MappedRecordCachingFailure(index, hubId, throwable, schema, result) => {
        log.error(s"Error during caching of record at index $index, hubId $hubId: ${throwable.getMessage}")
        onError(throwable)
      }
    }
    handleInterrupt()
  }

  private def handleInterrupt() {

    processingInterrupted.set(true)

    updateCount(0)

    if (processingContext.indexingSchema.isDefined) {
      log.info("Deleting DataSet %s from SOLR".format(processingContext.collection.spec))
      indexingServiceLocator.byDomain(configuration).deleteBySpec(processingContext.collection.getOwner, processingContext.collection.spec)(configuration)
    }

    whenDone()
  }

}

case class ProcessRecord(index: Int, hubId: HubId, sourceRecord: String, targetSchemas: Seq[SchemaVersion])
case object GetQueueSize

case object ProcessingDone
case object CancelProcessing