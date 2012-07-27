package jobs

import core.search.SolrServer
import play.api.Logger
import models.{DomainConfiguration, VirtualCollection}
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

      VirtualCollection.all.foreach {
        dao => {
          dao.find(MongoDBObject()) foreach {
            vc =>
              val c: Long = count(vc)
              dao.update(MongoDBObject("_id" -> vc._id), $set ("currentQueryCount" -> c))
          }

        }
      }


    case UpdateVirtualCollection =>
      VirtualCollection.all.foreach {
        dao => {
          dao.find(MongoDBObject("autoUpdate" -> true)) foreach {
            vc =>
              val currentCount = count(vc)
              if(currentCount != vc.getTotalRecords) {
                val domainConfiguration = DomainConfiguration.getAll.find(_.name == vc.query.domainConfiguration).get
                dao.createVirtualCollectionFromQuery(vc._id, vc.query.toSolrQuery, domainConfiguration, null) match {
                  case Right(computed) =>
                    log.info("Recomputed Virtual Collection %s, found %s records".format(vc.name, computed.getTotalRecords))
                  case Left(error) =>
                    Logger("CultureHub").error("Error computing virtual collection %s during periodic recomputation".format(vc.name), error)
                    ErrorReporter.reportError("Periodic Virtual Collection computer", error, "Error computing Virtual Collection " + vc.name, domainConfiguration)
                }
              }
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
