package actors

import akka.actor.Actor
import models.VirtualCollection
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import core.search.SolrServer
import org.apache.solr.client.solrj.SolrQuery

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class VirtualCollectionCount extends Actor with SolrServer {

  protected def receive = {

    case UpdateVirtualCollectionCount =>
      VirtualCollection.find(MongoDBObject()) foreach {
        vc =>
          val result = getSolrServer.query(new SolrQuery(vc.query.toSolrQuery + " delving_orgId:" + vc.orgId))
          val count = result.getResults.getNumFound
          VirtualCollection.update(MongoDBObject("_id" -> vc._id), $set ("currentQueryCount" -> count))
      }

    case _ =>

  }
}

case object UpdateVirtualCollectionCount
