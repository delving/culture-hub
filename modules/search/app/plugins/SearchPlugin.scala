package plugins

import play.api.Application
import core.CultureHubPlugin
import akka.actor.{ Props, ActorContext }
import actors.SolrCache
import services.{ SOLRIndexingService }
import services.search.SOLRSearchService

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class SearchPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "search"

  /**
   * Service instances this plugin provides
   */
  override def services: Seq[Any] = Seq(
    new SOLRSearchService,
    new SOLRIndexingService
  )

  override def onActorInitialization(context: ActorContext) {
    context.actorOf(Props[SolrCache])

  }
}
