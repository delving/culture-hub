package actors

import akka.actor._
import models._
import play.api.Logger
import controllers.ErrorReporter
import util.OrganizationConfigurationHandler

/**
 * Entry point for Processing. This actor deals with initializing the processing of a set when asked to do so
 * and deals with state changes.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class Processor extends Actor {

  private val log = Logger("CultureHub")

  def receive = {

    case ProcessDataSet(set) =>
      log.debug(s"Received set to process ${set.spec}")
      implicit val configuration = OrganizationConfigurationHandler.getByOrgId(set.orgId)

      try {

        // sanity check
        val currentState = DataSet.dao.getState(set.orgId, set.spec)

        if (currentState == DataSetState.PROCESSING_QUEUED) {
          DataSet.dao(set.orgId).updateState(set, DataSetState.PROCESSING)
          val dataSetCollectionProcessor = context.actorOf(Props[DataSetCollectionProcessor])

          dataSetCollectionProcessor ! ProcessDataSetCollection(set,
            onSuccess = { () =>
              DataSet.dao(set.orgId).updateProcessingInstanceIdentifier(set, None)
              val state = DataSet.dao.getState(set.orgId, set.spec)
              if (state == DataSetState.PROCESSING) {
                DataSet.dao.updateState(set, DataSetState.ENABLED)
              } else if (state == DataSetState.CANCELLED) {
                DataSet.dao.updateState(set, DataSetState.UPLOADED)
              }
              context.stop(dataSetCollectionProcessor)
            },
            onFailure = { t =>
              context.stop(dataSetCollectionProcessor)
              handleProcessingFailure(set, t)
            }, configuration)

        } else if (currentState != DataSetState.CANCELLED) {
          log.warn("Trying to process set %s which is not in PROCESSING_QUEUED state but in state %s".format(
            set.spec, currentState
          ))
        }

      } catch {
        case t: Throwable => {
          context.children foreach { context.stop(_) }
          handleProcessingFailure(set, t)
        }
      }
  }

  private def handleProcessingFailure(set: DataSet, t: Throwable)(implicit configuration: OrganizationConfiguration) {
    try {
      t.printStackTrace()
      log.error("Error while processing DataSet %s".format(set.spec), t)
      ErrorReporter.reportError(getClass.getName, "Error during processing of DataSet")
      DataSet.dao(set.orgId).updateProcessingInstanceIdentifier(set, None)
      DataSet.dao(set.orgId).updateState(set, DataSetState.ERROR, None, Some(t.getMessage))
    } catch {
      case t1: Throwable =>
        t1.printStackTrace()
      // not reporting here, since reporting probably happened
    }

  }

}

case class ProcessDataSet(set: DataSet)