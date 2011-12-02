package jobs

import play.jobs.Job
import com.mongodb.casbah.commons.MongoDBObject
import models.{Story, UserCollection, Thing, DObject}
import controllers.{ErrorReporter, SolrServer}
import org.apache.solr.client.solrj.response.UpdateResponse
import util.Constants._

/**
 * Index all things UGC
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class UGCIndexing extends Job with SolrServer {

  override def doJob() {
    index(OBJECT, DObject.find(MongoDBObject("deleted" -> false)))
    index(USERCOLLECTION, UserCollection.find(MongoDBObject("deleted" -> false)))
    index(STORY, Story.find(MongoDBObject("deleted" -> false)))
  }

  private def index(thingType: String, things: Iterator[Thing]) {
    val deleteResponse: UpdateResponse = getStreamingUpdateServer.deleteByQuery("delving_recordType:" + thingType)
    deleteResponse.getStatus
    getStreamingUpdateServer.commit

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