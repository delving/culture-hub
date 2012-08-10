package util

import core.CultureHubPlugin

/**
 * Test data
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

object TestDataLoader {

  def load() {
    CultureHubPlugin.hubPlugins.foreach { plugin =>
      plugin.onLoadTestData()
    }
  }

}
