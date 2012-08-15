package jobs

import akka.util.duration._
import akka.actor._
import models._
import play.api.Logger
import play.libs.Akka
import controllers.ErrorReporter
import util.DomainConfigurationHandler
import processing.DataSetCollectionProcessor

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class Processor extends Actor {

  private var scheduledTask: Cancellable = null


  override def preStart() {
    scheduledTask = Akka.system.scheduler.schedule(10 seconds, 10 seconds, self, PollDataSets)
  }


  override def postStop() {
    scheduledTask.cancel()
  }

  def receive = {

    case PollDataSets => {
      DataSet.all.flatMap(_.findCollectionForIndexing()).foreach {
        set => self ! ProcessDataSet(set)
      }
    }

    case ProcessDataSet(set) =>
      implicit val configuration = DomainConfigurationHandler.getByOrgId(set.orgId)
      try {
        DataSetCollectionProcessor.process(set)
      } catch {
        case t: Throwable => {
          t.printStackTrace()
          Logger("CultureHub").error("Error while processing DataSet %s".format(set.spec), t)
          ErrorReporter.reportError(getClass.getName, "Error during processing of DataSet")
          DataSet.dao(set.orgId).updateState(set, DataSetState.ERROR, None, Some(t.getMessage))
        }
      }

    case a@_ => Logger("CultureHub").warn("Processor: What what ? ==> " + a)

  }

}

case object PollDataSets
case class ProcessDataSet(set: DataSet)