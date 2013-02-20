package core.processing

import akka.actor.{ PoisonPill, Actor }
import core.HubId
import eu.delving.schema.SchemaVersion
import core.indexing.Indexing
import models.OrganizationConfiguration
import java.util.concurrent.atomic.AtomicBoolean
import play.api.Logger
import com.yammer.metrics.scala.Instrumented

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class RecordIndexer(processingContext: ProcessingContext, processingInterrupted: AtomicBoolean, configuration: OrganizationConfiguration) extends Actor with Instrumented {

  val counter = metrics.counter(processingContext.collection.getOwner + ".recordIndexer")

  val log = Logger("CultureHub")

  override def postStop() {
    counter.clear()
  }

  def receive = {

    case IndexRecord(hubId, schema, fields) =>

      if (processingInterrupted.get()) {
        self ! PoisonPill
      } else {
        Indexing.indexOne(processingContext.collection, hubId, fields, schema.getPrefix)(configuration)

        counter += 1

        if (log.isDebugEnabled) {
          if (counter.count % 5000 == 0) {
            log.debug(
              s"""Processing metrics from RecordIndexer:
                |- indexed records: ${counter.count}
              """.stripMargin)
          }
        }

      }

  }

}

case class IndexRecord(hubId: HubId, schema: SchemaVersion, fields: Map[String, List[String]])
