package jobs

import play.jobs.{Every, Job}
import models.DataSet

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 8/14/11 8:43 PM  
 */

@Every("10s")
class SolrIndexing extends Job {

  var isRunning:Boolean = false

  override def doJob() {
    // TODO this is probably useless, remove when tested
    if(!isRunning) {
      isRunning = true
      val dataSet = DataSet.findCollectionForIndexing()
      println("looking for datasets")
      if (dataSet != None) DataSet.indexInSolr(dataSet.get, "icn") // todo add default index format later via DataSet
      isRunning = false
    }
  }
}