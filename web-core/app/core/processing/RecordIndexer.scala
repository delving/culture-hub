package core.processing

import akka.actor.{PoisonPill, Actor}
import core.HubId
import eu.delving.schema.SchemaVersion
import core.indexing.Indexing
import models.OrganizationConfiguration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class RecordIndexer(processingContext: ProcessingContext, processingInterrupted: AtomicBoolean, configuration: OrganizationConfiguration) extends Actor {

  def receive = {

    case IndexRecord(hubId, schema, fields) =>

      if (processingInterrupted.get()) {
        self ! PoisonPill
      } else {
        Indexing.indexOne(processingContext.collection, hubId, fields, schema.getPrefix)(configuration)
      }

  }

}

case class IndexRecord(hubId: HubId, schema: SchemaVersion, fields: Map[String, List[String]])
