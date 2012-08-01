package actors

import akka.actor.Actor
import core.search.SolrServer
import play.api.cache.Cache
import play.api.Play.current
import util.DomainConfigurationHandler

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class SolrCache extends Actor {
  protected def receive = {

    case CacheSolrFields =>
      DomainConfigurationHandler.domainConfigurations.foreach { configuration =>
        val fields = SolrServer.computeSolrFields(configuration)
        Cache.set(SolrServer.SOLR_FIELDS_CACHE_KEY_PREFIX + configuration.name, fields)
      }
    case _ => // do nothing
  }
}

case object CacheSolrFields
