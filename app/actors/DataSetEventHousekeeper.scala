package actors

import akka.actor.Actor
import models.DataSetEventLog

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSetEventHousekeeper extends Actor {

  def receive = {
    case CleanupTransientEvents => DataSetEventLog.removeTransient()
  }

}

case object CleanupTransientEvents