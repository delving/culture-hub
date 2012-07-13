package jobs

import core.search.SolrServer
import play.api.Logger
import models.{PortalTheme, VirtualCollection}
import com.mongodb.casbah.Imports._
import controllers.ErrorReporter
import org.apache.solr.client.solrj.SolrQuery
import akka.actor._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class VirtualCollectionCount extends Actor with SolrServer {

  val log: Logger = Logger("CultureHub")

  protected def receive = {

    case UpdateVirtualCollectionCount =>
      VirtualCollection.find(MongoDBObject()) foreach {
        vc =>
          val c: Long = count(vc)
          VirtualCollection.update(MongoDBObject("_id" -> vc._id), $set ("currentQueryCount" -> c))
      }

    case UpdateVirtualCollection =>
      VirtualCollection.find(MongoDBObject("autoUpdate" -> true)) foreach {
        vc =>
          val currentCount = count(vc)
          if(currentCount != vc.getTotalRecords) {
            val portalTheme = PortalTheme.getAll.find(_.name == vc.query.theme).get
            VirtualCollection.createVirtualCollectionFromQuery(vc._id, vc.query.toSolrQuery, portalTheme, null) match {
              case Right(computed) =>
                log.info("Recomputed Virtual Collection %s, found %s records".format(vc.name, computed.getTotalRecords))
              case Left(error) =>
                Logger("CultureHub").error("Error computing virtual collection %s during periodic recomputation".format(vc.name), error)
                ErrorReporter.reportError("Periodic Virtual Collection computer", error, "Error computing Virtual Collection " + vc.name, portalTheme)
            }
          }
      }

    case _ =>

  }

  def count(vc: VirtualCollection): Long = {
    val result = getSolrServer.query(new SolrQuery(vc.query.toSolrQuery + " delving_orgId:" + vc.orgId))
    val count = result.getResults.getNumFound
    count
  }
}

case object UpdateVirtualCollectionCount

case object UpdateVirtualCollection
