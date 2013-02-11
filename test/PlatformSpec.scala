import core.CultureHubPlugin
import models.OrganizationConfiguration
import util.OrganizationConfigurationHandler

import play.api.test._
import play.api.test.Helpers._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class PlatformSpec extends Specs2TestContext {

  val organizationConfigurationHandler = OrganizationConfigurationHandler

  "The OrganizationConfigurationHandler handled" should {

    "load configurations from disk into memory" in {
      withTestConfig {

        organizationConfigurationHandler.startup(CultureHubPlugin.hubPlugins)
        OrganizationConfiguration.startup(CultureHubPlugin.hubPlugins).size should not equalTo (0)
      }
    }

  }

}
