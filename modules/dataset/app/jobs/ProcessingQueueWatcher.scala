package jobs

import akka.util.duration._
import akka.actor.{Cancellable, Actor}
import play.libs.Akka
import models.{DataSetState, DataSet}
import play.api.Play

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class ProcessingQueueWatcher extends Actor {

  private var scheduledTask: Cancellable = null

  private val processorRef = Akka.system.actorFor("akka://application/user/dataSetProcessor")

  override def preStart() {
    scheduledTask = Akka.system.scheduler.schedule(10 seconds, 10 seconds, self, PollDataSets)
  }


  override def postStop() {
    scheduledTask.cancel()
  }

  def receive = {

    case PollDataSets => {
      DataSet.all.flatMap(_.findCollectionForProcessing).foreach { set =>
        val instanceIdentifier = Play.current.configuration.getString("cultureHub.instanceIdentifier").getOrElse("default")
        DataSet.dao(set.orgId).updateProcessingInstanceIdentifier(set, Some(instanceIdentifier))
        DataSet.dao(set.orgId).updateState(set, DataSetState.PROCESSING_QUEUED)
        processorRef ! ProcessDataSet(set)
      }

      // handle cancelled sets
      DataSet.all.flatMap(_.findByState(DataSetState.CANCELLED)).foreach { set =>
        DataSet.dao(set.orgId).updateState(set, DataSetState.UPLOADED)
      }

    }

  }

}

case object PollDataSets
