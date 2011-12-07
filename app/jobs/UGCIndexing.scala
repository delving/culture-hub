/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    val deleteResponse: UpdateResponse = getStreamingUpdateServer.deleteByQuery("%s:%s".format(RECORD_TYPE, thingType))
    deleteResponse.getStatus
    getStreamingUpdateServer.commit

    things.zipWithIndex.foreach(el => {
      getStreamingUpdateServer add (el._1.toSolrDocument)
      if (el._2 % 100 == 0) {
        getStreamingUpdateServer.commit()
      }
    })
    getStreamingUpdateServer.commit()
  }

  override def onException(e: Throwable) {
    ErrorReporter.reportError(getClass.getName, e, "Error during indexing of UGC content")
  }

}