import core.CultureHubPlugin
import models.OrganizationConfiguration
import play.api.Play
import play.api.Play.current
import test.Specs2TestContext
import util.OrganizationConfigurationHandler

import play.api.test._
import play.api.test.Helpers._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class PlatformSpec extends Specs2TestContext {

  val organizationConfigurationHandler = OrganizationConfigurationHandler

  "The OrganizationConfigurationHandler" should {

    "load configurations from disk into memory" in {
      withTestConfig {

        organizationConfigurationHandler.configure()
        val (configurations, errors) = OrganizationConfiguration.buildConfigurations(Play.application.configuration, CultureHubPlugin.hubPlugins)
        configurations.size should not equalTo (0)
        errors.size should equalTo(0)
      }
    }

  }

}