package plugins

import play.api.Application
import core.CultureHubPlugin
import services.EADIndexingAnalysisService

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class EADPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "ead"

  override def services: Seq[Any] = Seq(
    new EADIndexingAnalysisService
  )
}
