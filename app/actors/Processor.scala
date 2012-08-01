package actors

import akka.actor._
import models._
import play.api.Logger
import core.processing.DataSetCollectionProcessor
import play.libs.Akka
import controllers.ErrorReporter
import util.DomainConfigurationHandler

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class Processor extends Actor {

  val processingRouter = Akka.system.actorFor("akka://application/user/dataSetProcessor")

  def receive = {

    case PollDataSets => {
      DataSet.all.flatMap(_.findCollectionForIndexing()).foreach {
        set => processingRouter ! ProcessDataSet(set)
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
          ErrorReporter.reportError(getClass.getName, "Error during processing of DataSet", configuration)
          DataSet.dao(set.orgId).updateState(set, DataSetState.ERROR, None, Some(t.getMessage))
        }
      }

    case a@_ => Logger("CultureHub").warn("Processor: What what ? ==> " + a)

  }

}

case object PollDataSets

case class ProcessDataSet(dataSet: DataSet)