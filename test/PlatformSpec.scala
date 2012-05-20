import com.mongodb.casbah.commons.MongoDBObject
import models.PortalTheme
import org.specs2.mutable.Specification
import util.ThemeHandler

import play.api.test._
import play.api.test.Helpers._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class PlatformSpec extends Specification with TestContext {

  val themeHandler = ThemeHandler

  "The PortalTheme handled" should {

    "load themes from disk into the database" in {
      withTestConfig {
        themeHandler.startup()
        PortalTheme.find(MongoDBObject()).size should not equalTo (0)
      }
    }

    "deliver a default theme" in {
      withTestConfig {
        themeHandler.getDefaultTheme should not equalTo (null)
      }
    }

  }

  step(cleanup)


}
