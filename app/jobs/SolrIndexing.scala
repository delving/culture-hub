package jobs

import play.jobs.{Every, Job}
import components.Indexing
import models.{DataSetState, DataSet}
import play.Logger

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 8/14/11 8:43 PM  
 */

@Every("10s")
class SolrIndexing extends Job {

  override def doJob() {
    if (play.Play.started) {
      val dataSet = DataSet.findCollectionForIndexing()
      if (dataSet != None) {
        dataSet.get.idxMappings foreach {
          prefix =>
            try {
              Indexing.indexInSolr(dataSet.get, prefix)
            } catch {
              case t => {
                Logger.error(t, "Error while processing indexing for DataSet %s with mapping prefix %s".format(dataSet.get.spec, prefix))
                DataSet.updateState(dataSet.get, DataSetState.ERROR)
              } 
            }
        }
      }
    }
  }
}