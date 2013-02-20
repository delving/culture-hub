package core.processing

import eu.delving.schema.SchemaVersion
import akka.actor.{ PoisonPill, Actor }
import eu.delving.{ MappingResult, MappingEngineFactory, MappingEngine }
import play.api.{ Logger, Play }
import play.api.Play.current
import core.mapping.MappingService
import scala.collection.JavaConverters._
import core.{ SchemaService, HubModule, HubId }
import java.util.concurrent.atomic.AtomicBoolean
import com.yammer.metrics.scala.Instrumented
import com.yammer.metrics.Metrics
import com.yammer.metrics.core.MetricsRegistry
import java.util.concurrent.TimeUnit

/**
 * This actor maps single records from a source format to multiple output formats.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class RecordMapper(context: ProcessingContext, processingInterrupted: AtomicBoolean) extends Actor with Instrumented {

  val m = new MetricsRegistry()

  val counter = metrics.counter(context.collection.getOwner + ".recordMappingCount")
  val timer = metrics.timer(context.collection.getOwner + ".recordMappingTimer", "mapped records", durationUnit = TimeUnit.MILLISECONDS, rateUnit = TimeUnit.SECONDS)
  val log = Logger("CultureHub")

  private val schemaService = HubModule.inject[SchemaService](name = None)

  private val engines: Map[SchemaVersion, MappingEngine] = {
    context.targetSchemas.filter(_.mapping.isDefined).map { schema =>
      (schema.schemaVersion -> MappingEngineFactory.newInstance(Play.classloader, context.sourceNamespaces.asJava, MappingService.recDefModel(schemaService), schema.mapping.get))
    }
  }.toMap

  override def postStop() {
    counter.clear()
  }

  def receive = {

    case MapRecord(index, hubId, sourceRecord, targetSchemas) =>

      if (processingInterrupted.get()) {
        self ! PoisonPill
      } else {
        val results = timer.time {
          targetSchemas.flatMap { schema =>
            engines.get(schema).flatMap { engine =>
              try {
                Some((schema -> engine.execute(hubId.localId, sourceRecord)))
              } catch {
                case t: Throwable =>
                  sender ! RecordMappingFailure(index, hubId, sourceRecord, t)
                  None
              }
            }
          }.toMap
        }

        counter += 1

        if (log.isDebugEnabled) {
          if (counter.count % 5000 == 0) {
            log.debug(
              s"""Processing metrics from RecordMapper:
                |- mapped records: ${counter.count}
                |- mapping duration: ${timer.mean} ms
              """.stripMargin)
          }
        }

        sender ! RecordMappingResult(index, hubId, results)
      }

  }

}

case class MapRecord(index: Int, hubId: HubId, sourceRecord: String, targetSchemas: Seq[SchemaVersion])

case class RecordMappingResult(index: Int, hubId: HubId, results: Map[SchemaVersion, MappingResult])
case class RecordMappingFailure(index: Int, hubId: HubId, sourceRecord: String, throwable: Throwable)
