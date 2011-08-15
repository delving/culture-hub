package jobs

import play.jobs.{Every, Job}

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 8/14/11 8:43 PM  
 */

@Every("10s")
class SolrIndexing extends Job {
  override def doJob() {
    import models.DataSet
    val dataSet = DataSet.findCollectionForIndexing()
    println("looking for datasets")
    if (dataSet != None) DataSet.indexInSolr(dataSet.get, "icn") // todo add default index format later via DataSet
  }
}