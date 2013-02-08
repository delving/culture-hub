package actors

import akka.actor.{Cancellable, Actor}
import core.search.SolrServer
import play.api.cache.Cache
import play.api.Play.current
import util.OrganizationConfigurationHandler
import play.api.libs.concurrent.Akka
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class SolrCache extends Actor {

  private var scheduler: Cancellable = null


  override def preStart() {
    scheduler = Akka.system.scheduler.schedule(
      30 seconds,
      120 minutes,
      self,
      CacheSolrFields
    )
  }


  override def postStop() {
    scheduler.cancel()
  }

  def receive = {

    case CacheSolrFields =>
      OrganizationConfigurationHandler.organizationConfigurations.foreach { configuration =>
        val fields = SolrServer.computeSolrFields(configuration)
        Cache.set(SolrServer.SOLR_FIELDS_CACHE_KEY_PREFIX + configuration.name, fields)
      }
    case _ => // do nothing
  }
}

case object CacheSolrFields
