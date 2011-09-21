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

  override def doJob() {
    val dataSet = DataSet.findCollectionForIndexing()
    if (dataSet != None) DataSet.indexInSolr(dataSet.get, "icn") // todo add default index format later via DataSet
  }
}