package plugins

import play.api.Application
import core.CultureHubPlugin
import collection.immutable.ListMap
import scala.util.matching.Regex
import play.api.mvc.Handler
import controllers.api.IndexItemOrganizationCollectionLookupService

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class IndexApiPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "indexApi"

  /**
   * Service instances this plugin provides
   */
  override def services: Seq[Any] = Seq(
    new IndexItemOrganizationCollectionLookupService
  )
}