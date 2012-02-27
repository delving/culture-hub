package actors

import akka.actor.Actor
import core.search.SolrServer
import play.api.cache.Cache
import play.api.Play.current

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class SolrCache extends Actor {
  protected def receive = {

    case CacheSolrFields =>
      val fields = SolrServer.computeSolrFields
      Cache.set(SolrServer.SOLR_FIELDS, fields)
    case _ => // do nothing
  }
}

case object CacheSolrFields
