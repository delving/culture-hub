package jobs

import models.DataSetEventLog
import akka.actor.Actor

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSetEventHousekeeper extends Actor {

  def receive = {
    case CleanupTransientEvents => DataSetEventLog.all.foreach(l => l.removeTransient())
  }

}

case object CleanupTransientEvents