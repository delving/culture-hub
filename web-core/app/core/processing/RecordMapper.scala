package core.processing

import eu.delving.schema.SchemaVersion
import akka.actor.{PoisonPill, Actor}
import eu.delving.{MappingResult, MappingEngineFactory, MappingEngine}
import play.api.{Logger, Play}
import play.api.Play.current
import core.mapping.MappingService
import scala.collection.JavaConverters._
import core.{SchemaService, HubModule, HubId}
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This actor maps single records from a source format to multiple output formats.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class RecordMapper(context: ProcessingContext, processingInterrupted: AtomicBoolean) extends Actor {

  private val log = Logger("CultureHub")

  private val schemaService = HubModule.inject[SchemaService](name = None)

  private val engines: Map[SchemaVersion, MappingEngine] = {
//    val factory = new MappingEngineFactory(
//      Play.classloader,
//      Executors.newSingleThreadExecutor(),
//      MappingService.recDefModel
//    )
//    val engine = factory.createEngine(context.sourceNamespaces.asJava)
//    context.targetSchemas.filter(_.mapping.isDefined).foreach { schema =>
//      engine.addMappingRunner(schema.schemaVersion, schema.mapping.get)
//    }
//    engine
    context.targetSchemas.filter(_.mapping.isDefined).map { schema =>
      (schema.schemaVersion -> MappingEngineFactory.newInstance(Play.classloader, context.sourceNamespaces.asJava, MappingService.recDefModel(schemaService), schema.mapping.get))
    }
  }.toMap

  def receive = {

    case MapRecord(index, hubId, sourceRecord, targetSchemas) =>

      if (processingInterrupted.get()) {
        self ! PoisonPill
      } else {
        val results = targetSchemas.flatMap { schema =>
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

        sender ! RecordMappingResult(index, hubId, results)

//        engine.mapRecord(index, hubId.localId, sourceRecord, targetSchemas.toArray, new MappingCompletion {
//
//          def onFailure(index: Int, throwable: Throwable) {
//            sender ! RecordMappingFailure(index, hubId, sourceRecord, throwable)
//          }
//
//          def onSuccess(index: Int, results: java.util.Map[SchemaVersion, MappingResult]) {
//            sender ! RecordMappingResult(index, hubId, results.asScala.toMap)
//          }
//
//        })
      }

  }

}

case class MapRecord(index: Int, hubId: HubId, sourceRecord: String, targetSchemas: Seq[SchemaVersion])

case class RecordMappingResult(index: Int, hubId: HubId, results: Map[SchemaVersion, MappingResult])
case class RecordMappingFailure(index: Int, hubId: HubId, sourceRecord: String, throwable: Throwable)
