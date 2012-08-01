package actors

import akka.actor._
import models._
import play.api.Logger
import core.processing.DataSetCollectionProcessor

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
          DataSetCollectionProcessor.process(dataSet.get)
        } catch {
          case t => {
            t.printStackTrace()
            Logger("CultureHub").error("Error while processing DataSet %s".format(dataSet.get.spec), t)
            // TODO organization --> theme
            // ErrorReporter.reportError(getClass.getName, t, "Error during indexing of DataSet")
            DataSet.updateState(dataSet.get, DataSetState.ERROR, None, Some(t.getMessage))
          }
        }
      }
    }

    case a@_ => Logger("CultureHub").warn("Processor: What what ? ==> " + a)

  }

}

case class ProcessDataSets()
