package jobs

import play.jobs.{Every, Job}
import components.Indexing
import models.{DataSetState, DataSet}

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 8/14/11 8:43 PM  
 */

@Every("10s")
class SolrIndexing extends Job {

  override def doJob() {
    val dataSet = DataSet.findCollectionForIndexing()
    if (dataSet != None) {
      dataSet.get.indexingMappings foreach {
        prefix =>
          try {
            Indexing.indexInSolr(dataSet.get, prefix)
          } catch {
            case t => DataSet.updateState(dataSet.get, DataSetState.ERROR)
          }
      }
    }
  }
}