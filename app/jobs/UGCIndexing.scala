package jobs

import play.jobs.Job
import com.mongodb.casbah.commons.MongoDBObject
import models.{Story, UserCollection, Thing, DObject}
import controllers.{ErrorReporter, SolrServer}

/**
 * Index all things UGC
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class UGCIndexing extends Job with SolrServer {

  override def doJob() {
    index(DObject.find(MongoDBObject("deleted" -> false)))
    index(UserCollection.find(MongoDBObject("deleted" -> false)))
    index(Story.find(MongoDBObject("deleted" -> false)))
  }

  private def index(things: Iterator[Thing]) {
    things.zipWithIndex.foreach(el => {
      getStreamingUpdateServer add (el._1.toSolrDocument)
      if (el._2 % 100 == 0) {
        getStreamingUpdateServer.commit()
      }
    })
  }

  override def onException(e: Throwable) {
    ErrorReporter.reportError(getClass.getName, e, "Error during indexing of UGC content")
  }

}