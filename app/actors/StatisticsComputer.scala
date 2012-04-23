package actors

import akka.actor.Actor
import models._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class StatisticsComputer extends Actor {

  protected def receive = {

    case ComputeStatistics =>

      val lastRunDate = StatisticsRun.lastRun

      // insert new run
      StatisticsRun.insert(StatisticsRun())

      // ~~~ access statistics

      ProviderStatistics.computeAndSaveAccessStatistics(lastRunDate)
      DataProviderStatistics.computeAndSaveAccessStatistics(lastRunDate)
      CollectionStatistics.computeAndSaveAccessStatistics(lastRunDate)




    case _ => // do nothing



  }

}

case object ComputeStatistics