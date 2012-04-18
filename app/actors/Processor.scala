package actors

import akka.actor._
import models._
import play.api.Logger
import core.processing.DataSetProcessor

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class Processor extends Actor {

  def receive = {

    case ProcessDataSets => {
      val dataSet = DataSet.findCollectionForIndexing()
      if (dataSet != None) {
        try {
          DataSetProcessor.process(dataSet.get)
        } catch {
          case t => {
            Logger("CultureHub").error("Error while processing DataSet %s".format(dataSet.spec), t)
            // TODO organization --> theme
            // ErrorReporter.reportError(getClass.getName, t, "Error during indexing of DataSet")
            DataSet.updateState(dataSet.get, DataSetState.ERROR)
          }
        }
      }
    }

    case a@_ => Logger("CultureHub").warn("Processor: What what ? ==> " + a)

  }

}

case class ProcessDataSets()
