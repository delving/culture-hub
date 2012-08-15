package jobs

import core.search.SolrServer
import play.api.Logger
import models.{DomainConfiguration, VirtualCollection}
import com.mongodb.casbah.Imports._
import controllers.ErrorReporter
import org.apache.solr.client.solrj.SolrQuery
import akka.actor._
import plugins.CustomHarvestCollectionPlugin
import util.DomainConfigurationHandler
import core.CultureHubPlugin

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class VirtualCollectionCount extends Actor with SolrServer {

  val log: Logger = Logger("CultureHub")

  lazy val affectedDAOs = VirtualCollection.byConfiguration.
    filter(c => c._1.plugins.contains(CustomHarvestCollectionPlugin.CUSTOM_HARVEST_COLLECTION_KEY))
    .map(_._2)

  protected def receive = {

    case UpdateVirtualCollectionCount =>

      affectedDAOs.foreach {
        dao => {
          dao.find(MongoDBObject()) foreach {
            vc =>
              val c: Long = count(vc)
              dao.update(MongoDBObject("_id" -> vc._id), $set("currentQueryCount" -> c))
          }
        }
      }


    case UpdateVirtualCollection =>

      affectedDAOs.foreach {
        dao => {
          dao.find(MongoDBObject("autoUpdate" -> true)) foreach {
            vc =>
              val currentCount = count(vc)
              if (currentCount != vc.getTotalRecords) {
                implicit val configuration = DomainConfigurationHandler.getByName(vc.query.domainConfiguration)
                dao.createVirtualCollectionFromQuery(vc._id, vc.query.toSolrQuery, None) match {
                  case Right(computed) =>
                    log.info("Recomputed Virtual Collection %s, found %s records".format(vc.name, computed.getTotalRecords))
                  case Left(error) =>
                    Logger("CultureHub").error("Error computing virtual collection %s during periodic recomputation".format(vc.name), error)
                    ErrorReporter.reportError("Periodic Virtual Collection computer", error, "Error computing Virtual Collection " + vc.name)
                }
              }
          }
        }
      }

    case _ =>

  }

  def count(vc: VirtualCollection): Long = {
    val result = getSolrServer(DomainConfigurationHandler.getByOrgId(vc.orgId)).query(new SolrQuery(vc.query.toSolrQuery + " delving_orgId:" + vc.orgId))
    val count = result.getResults.getNumFound
    count
  }
}

case object UpdateVirtualCollectionCount

case object UpdateVirtualCollection
