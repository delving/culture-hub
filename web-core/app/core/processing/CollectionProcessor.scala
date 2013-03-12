package core.processing

import scala.collection.JavaConverters._
import play.api.Logger
import core.collection.{ OrganizationCollectionInformation, Collection }
import core.storage.BaseXStorage
import core.indexing.IndexingService
import models._
import xml.{ Elem, NodeSeq, Node }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }
import akka.actor.{ SupervisorStrategy, Actor, Props }
import akka.pattern.ask
import core.HubId
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import concurrent.{ Await, Future }
import akka.util.Timeout
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy._

/**
 * CollectionProcessor, essentially taking care of:
 *
 * - iterating over all records
 * - running the primary mappings and derived ones for each valid record, for each selected target schema
 * - extracting system fields for the given renderingSchema and together with the serialized result caching them in the MetadataCache
 * - indexing the record in the selected indexingSchema
 *
 */
class CollectionProcessor(collection: Collection with OrganizationCollectionInformation,
    sourceNamespaces: Map[String, String],
    targetSchemas: List[ProcessingSchema],
    indexingSchema: Option[ProcessingSchema],
    renderingSchema: Option[ProcessingSchema],
    interrupted: => Boolean,
    updateCount: Long => Unit,
    onError: Throwable => Unit,
    indexOne: (MetadataItem, MultiMap, String) => Either[Throwable, String],
    onProcessingDone: ProcessingContext => Unit,
    whenDone: () => Unit,
    basexStorage: BaseXStorage)(implicit configuration: OrganizationConfiguration) extends Actor {

  val log = Logger("CultureHub")

  private implicit def listMapToScala(map: java.util.Map[String, java.util.List[String]]) = map.asScala.map(v => (v._1, v._2.asScala.toList)).toMap

  class ChildSelectable(ns: NodeSeq) {
    def \* = ns flatMap {
      _ match {
        case e: Elem => e.child
        case _ => NodeSeq.Empty
      }
    }
  }

  implicit def nodeSeqIsChildSelectable(xml: NodeSeq) = new ChildSelectable(xml)

  override def preRestart(reason: Throwable, message: Option[Any]) {
    log.error("CollectionProcessor was restarted because " + reason.getMessage, reason)
    onError(reason)
    super.preRestart(reason, message)
  }

  def receive = {

    case DoProcess =>

      val targetSchemasString = targetSchemas.map(_.prefix).mkString(", ")

      log.info("Starting processing of collection '%s': going to process schemas '%s', schema for indexing is '%s', format for rendering is '%s'".format(
        collection.spec, targetSchemasString, indexingSchema.map(_.prefix).getOrElse("NONE!"), renderingSchema.map(_.prefix).getOrElse("NONE!"))
      )

      try {
        basexStorage.withSession(collection) {
          implicit session =>
            {
              var record: Node = null
              var index: Int = 0

              updateCount(0)

              val recordsProcessed = new AtomicInteger(0)
              val interruptedFlag = new AtomicBoolean(false)

              var inError: Boolean = false

              try {
                val recordCount = basexStorage.count
                val records = basexStorage.findAllCurrent

                val processingContext = ProcessingContext(collection, targetSchemas, sourceNamespaces, renderingSchema.map(_.schemaVersion), indexingSchema.map(_.schemaVersion))

                val supervisorProps = Props(new ProcessingSupervisor(
                  recordCount,
                  updateCount,
                  interrupted,
                  onProcessingDone,
                  whenDone,
                  onError,
                  processingContext,
                  configuration
                ))

                val supervisor = context.actorOf(supervisorProps)

                records.zipWithIndex.foreach { r =>
                  if (!interruptedFlag.get()) {
                    record = r._1
                    index = r._2

                    val localId = (record \ "@id").text
                    val hubId = HubId(collection.getOwner, collection.spec, localId)
                    val recordIndex = (record \ "system" \ "index").text.toInt
                    val sourceRecord: String = (record \ "document" \ "input" \*).mkString("\n")
                    val schemas = targetSchemas.filter(targetSchema => targetSchema.isValidRecord(recordIndex) && targetSchema.sourceSchema == "raw")

                    supervisor ! ProcessRecord(index, hubId, sourceRecord, schemas.map(_.schemaVersion))

                    val processed = recordsProcessed.incrementAndGet()

                    if (processed % 100 == 0) {
                      interruptedFlag.set(interrupted)
                    }

                    implicit val timeout: Timeout = 5 seconds

                    // dynamic throttling of the messages we send off to Akka
                    // we might not be able to process the data coming from the database fast enough, and fill the memory with dozens of messages as a consequence
                    // hence we check here what the estimated queue size is and sleep if necessary
                    def throttleRecords() {
                      val maybeQueueSize: Future[Any] = supervisor ? GetQueueSize

                      try {
                        val size = Await.result(maybeQueueSize, 5 seconds)
                        if (size.asInstanceOf[Int] > 5000) {
                          log.debug(s"[CollectionProcessor ${collection.spec}] Source records queued, current queue size is $size, sleeping")
                          Thread.sleep(5000)
                          throttleRecords()
                        }
                      } catch {
                        case t: Throwable =>
                          throttleRecords()

                      }

                    }

                    if (processed % 200 == 0) {
                      throttleRecords()
                    }

                  }
                }

              } catch {
                case t: Throwable => {
                  inError = true
                  t.printStackTrace()

                  log.error("""Error while processing records of collection %s of organization %s, at index %s
                |
                |Source record:
                |
                |%s
                |
                """.stripMargin.format(collection.spec, collection.getOwner, index, record), t)

                  if (indexingSchema.isDefined) {
                    log.info("Deleting DataSet %s from SOLR".format(collection.spec))
                    IndexingService.deleteBySpec(collection.getOwner, collection.spec)
                  }

                  updateCount(0)
                  log.error("Unexpected error while processing collection %s of organization %s: %s".format(collection.spec, collection.getOwner, t.getMessage), t)
                  onError(t)
                }

              }
            }
        }
      } catch {
        case c: java.net.ConnectException =>
          log.error(s"Cannot connect to BaseX server")
          onError(new RuntimeException("Cannot reach BaseX server", c))
        case t: Throwable => {
          log.error("Error while processing collection %s of organization %s, cannot read source data: %s".format(collection.spec, collection.getOwner, t.getMessage), t)
          context.children foreach { context.stop(_) }
          onError(t)
        }

      }
  }
}

case object DoProcess
