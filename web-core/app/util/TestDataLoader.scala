package util

import core.CultureHubPlugin
import play.api.Play
import play.api.Play.current


/**
 * Test data
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

object TestDataLoader {

  lazy val hubPlugins: List[CultureHubPlugin] = Play.application.plugins.
    filter(_.isInstanceOf[CultureHubPlugin]).
    map(_.asInstanceOf[CultureHubPlugin]).
    toList

  def load() {
    hubPlugins.foreach { plugin =>
      plugin.onLoadTestData()
    }
  }

}
