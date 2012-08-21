package jobs

import akka.actor._
import models._
import play.api.Logger
import controllers.ErrorReporter
import util.DomainConfigurationHandler
import processing.DataSetCollectionProcessor

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class Processor extends Actor {

  def receive = {

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

case class ProcessDataSet(set: DataSet)