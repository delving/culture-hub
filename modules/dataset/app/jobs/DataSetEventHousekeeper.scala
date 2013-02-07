package jobs

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import models.DataSetEventLog
import akka.actor.{Cancellable, Actor}
import play.api.libs.concurrent.Akka
import play.api.Play.current

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSetEventHousekeeper extends Actor {

  private var scheduledTask: Cancellable = null


  override def preStart() {
    scheduledTask = Akka.system.scheduler.schedule(20 seconds, 30 minutes, self, CleanupTransientEvents)
  }


  override def postStop() {
    scheduledTask.cancel()
  }


  def receive = {
    case CleanupTransientEvents => DataSetEventLog.all.foreach(l => l.removeTransient())
  }

}

case object CleanupTransientEvents