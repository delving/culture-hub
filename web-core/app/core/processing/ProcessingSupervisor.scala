package core.processing

import akka.actor.{PoisonPill, Props, Actor}
import core.HubId
import collection.mutable.ArrayBuffer
import eu.delving.schema.SchemaVersion
import models.OrganizationConfiguration
import play.api.Logger
import core.indexing.IndexingService
import akka.util.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import akka.routing.RoundRobinRouter

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
  interrupted: => Boolean,
  onProcessingDone: ProcessingContext => Unit,
  whenDone: => Unit,
  onError: Throwable => Unit,
  processingContext: ProcessingContext,
  configuration: OrganizationConfiguration
) extends Actor {

  private val log = Logger("CultureHub")

  private val processingInterrupted = new AtomicBoolean(false)

  private val numCores = (math.round(Runtime.getRuntime.availableProcessors() * configuration.processingService.mappingCpuProportion)).toInt

  private val recordMapper = context.actorOf(Props(new RecordMapper(processingContext, processingInterrupted)).withRouter(
    RoundRobinRouter(nrOfInstances = numCores))
  )
  private val recordCacher = context.actorOf(Props(new MappedRecordCacher(processingContext, processingInterrupted)))
  private val recordIndexer = context.actorOf(Props(new RecordIndexer(processingContext, processingInterrupted, configuration)))

  private var numSourceRecords: Int = 0
  private var numMappingResults: Int = 0
  private val mappingFailures = new ArrayBuffer[(Int, HubId, String, Throwable)]

  private val modulo = math.round(totalSourceRecords / 100)


  def receive = {

    case ProcessRecord(index, hubId, sourceRecord, targetSchemas) =>
      numSourceRecords = numSourceRecords + 1
      recordMapper ! MapRecord(index, hubId, sourceRecord, targetSchemas)
      sender ! (numSourceRecords - numMappingResults)

    case RecordMappingResult(index, hubId, results) =>
      numMappingResults = numMappingResults + 1

      val tick = numMappingResults % (if(modulo == 0) 100 else modulo) == 0

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

            recordIndexer ! IndexRecord(hubId, indexingSchema, fieldsToIndex)
          }
        }

        if (numMappingResults == totalSourceRecords) {
          self ! ProcessingDone
        }

      }

      if (tick) {
        updateCount(numMappingResults)
      }

      if (numMappingResults % 2000 == 0) {
        log.info("%s:%s: processed %s of %s records, for schemas '%s'".format(
          processingContext.collection.getOwner, processingContext.collection.spec, numMappingResults, totalSourceRecords, processingContext.targetSchemasString)
        )
      }

    case f@RecordMappingFailure(index, hubId, sourceRecord, throwable) =>
      mappingFailures += ((index, hubId, sourceRecord, throwable))
      handleError(f)

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
        whenDone
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
      case MappedRecordCachingFailure(index, hubId, schema, result, throwable) =>
        log.error(
          """
            |Error during caching of record at index %s for schema %s, hubId %s: %s
            |
            |Mapping result:
            |---------------
            |
            |%s
            |
          """.stripMargin.format(index, schema, hubId, throwable.getMessage, result), throwable)
        onError(throwable)

    }
    handleInterrupt()
  }

  private def handleInterrupt() {

    processingInterrupted.set(true)

    updateCount(0)

    if (processingContext.indexingSchema.isDefined) {
      log.info("Deleting DataSet %s from SOLR".format(processingContext.collection.spec))
      IndexingService.deleteBySpec(processingContext.collection.getOwner, processingContext.collection.spec)(configuration)
    }

    whenDone
  }

}

case class ProcessRecord(index: Int, hubId: HubId, sourceRecord: String, targetSchemas: Seq[SchemaVersion])
case object RecordProcessing

case object ProcessingDone
case object CancelProcessing