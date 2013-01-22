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

  private val log = Logger("CultureHub")

  def receive = {

    case ProcessDataSet(set) =>
      implicit val configuration = DomainConfigurationHandler.getByOrgId(set.orgId)

      try {

        // sanity check
        val currentState = DataSet.dao.getState(set.orgId, set.spec)

        if(currentState == DataSetState.PROCESSING_QUEUED) {
          DataSet.dao(set.orgId).updateState(set, DataSetState.PROCESSING)
          DataSetCollectionProcessor.process(set, {
            val state = DataSet.dao.getState(set.orgId, set.spec)
            if(state == DataSetState.PROCESSING) {
              DataSet.dao.updateState(set, DataSetState.ENABLED)
            } else if(state == DataSetState.CANCELLED) {
              DataSet.dao.updateState(set, DataSetState.UPLOADED)
            }
          })
        } else if(currentState != DataSetState.CANCELLED) {
          log.warn("Trying to process set %s which is not in PROCESSING_QUEUED state but in state %s".format(
            set.spec, currentState
          ))
        }

      } catch {
        case t: Throwable => {
          try {
            t.printStackTrace()
            log.error("Error while processing DataSet %s".format(set.spec), t)
            ErrorReporter.reportError(getClass.getName, "Error during processing of DataSet")
            DataSet.dao(set.orgId).updateState(set, DataSetState.ERROR, None, Some(t.getMessage))
          } catch {
            case t1: Throwable =>
              t1.printStackTrace()
              // not reporting here, since reporting probably happened
          }
        }
      }

    case a@_ => log.warn("Processor: What what ? ==> " + a)

  }

}

case class ProcessDataSet(set: DataSet)