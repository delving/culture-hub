package jobs

import akka.util.duration._
import akka.actor.{Cancellable, Actor}
import play.libs.Akka
import models.DataSet

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
      DataSet.all.flatMap(_.findCollectionForIndexing()).foreach {
        set => processorRef ! ProcessDataSet(set)
      }
    }

  }

}

case object PollDataSets
