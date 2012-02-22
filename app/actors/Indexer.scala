package actors

import akka.actor._
import models._
import play.api.Logger
import controllers.ErrorReporter
import core.indexing.Indexing

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class Indexer extends Actor {

  def receive = {

    case IndexDataSets => {
      val dataSet = DataSet.findCollectionForIndexing()
      if (dataSet != None) {
        dataSet.get.getIndexingMappingPrefix match {
          case Some(prefix) =>
            try {
              Indexing.indexInSolr(dataSet.get, prefix)
            } catch {
              case t => {
                Logger("CultureHub").error("Error while processing indexing for DataSet %s with mapping prefix %s".format(dataSet.get.spec, prefix), t)
                // TODO organization --> theme
//                ErrorReporter.reportError(getClass.getName, t, "Error during indexing of DataSet")
                DataSet.updateState(dataSet.get, DataSetState.ERROR)
              }
            }
          case None =>
            Logger("CultureHub").error("No indexing mapping prefix set for DataSet " + dataSet.get.spec)
                // TODO organization --> theme
//            ErrorReporter.reportError(getClass.getName, new RuntimeException(), "ENo indexing mapping prefix set for DataSet " + dataSet.get.spec)
            DataSet.updateState(dataSet.get, DataSetState.ERROR)
        }
      }
    }

    case a@_ => Logger("CultureHub").warn("Indexer: What what ? ==> " + a)

  }

}

case class IndexDataSets()
