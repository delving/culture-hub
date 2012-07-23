import models.DomainConfiguration
import org.specs2.mutable.Specification
import util.DomainConfigurationHandler

import play.api.test._
import play.api.test.Helpers._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class PlatformSpec extends Specification with TestContext {

  val domainConfigurationHandler = DomainConfigurationHandler

  "The DomainConfigurationHandler handled" should {

    "load configurations from disk into memory" in {
      withTestConfig {
        domainConfigurationHandler.startup()
        DomainConfiguration.getAll.size should not equalTo (0)
      }
    }

    "deliver a default configuration" in {
      withTestConfig {
        domainConfigurationHandler.getDefaultConfiguration should not equalTo (null)
      }
    }

  }

}
