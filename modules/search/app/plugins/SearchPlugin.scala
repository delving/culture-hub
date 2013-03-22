package plugins

import play.api.Application
import core.CultureHubPlugin
import core.search.SOLRSearchService
import core.indexing.SOLRIndexingService

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
}
