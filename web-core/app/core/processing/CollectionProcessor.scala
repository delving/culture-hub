package core.processing

import scala.collection.JavaConverters._
import play.api.Logger
import core.collection.{OrganizationCollectionInformation, Collection}
import core.storage.BaseXStorage
import core.indexing.IndexingService
import models._
import xml.{Elem, NodeSeq, Node}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import akka.actor.{Actor, Props}
import core.HubId

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
                          onProcessingFinalize: () => Unit,
                          basexStorage: BaseXStorage)(implicit configuration: DomainConfiguration) extends Actor {

  val log = Logger("CultureHub")

  private implicit def listMapToScala(map: java.util.Map[String, java.util.List[String]]) = map.asScala.map(v => (v._1, v._2.asScala.toList)).toMap

  class ChildSelectable(ns: NodeSeq) {
    def \* = ns flatMap { _ match {
      case e: Elem => e.child
      case _ => NodeSeq.Empty
    } }
  }

  implicit def nodeSeqIsChildSelectable(xml: NodeSeq) = new ChildSelectable(xml)


  def receive = {

    case DoProcess =>

      val targetSchemasString = targetSchemas.map(_.prefix).mkString(", ")

      log.info("Starting processing of collection '%s': going to process schemas '%s', schema for indexing is '%s', format for rendering is '%s'".format(
        collection.spec, targetSchemasString, indexingSchema.map(_.prefix).getOrElse("NONE!"), renderingSchema.map(_.prefix).getOrElse("NONE!"))
      )

      try {
        basexStorage.withSession(collection) {
          implicit session => {
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
                onProcessingFinalize,
                onError,
                processingContext,
                configuration
              ))

              val supervisor = context.actorOf(supervisorProps, name = "processingSupervisor-" + collection.getOwner)

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
        case t: Throwable => {
          t.printStackTrace()
          log.error("Error while processing collection %s of organization %s, cannot read source data: %s".format(collection.spec, collection.getOwner, t.getMessage), t)
          onError(t)
        }

      }
  }
}

case object DoProcess
