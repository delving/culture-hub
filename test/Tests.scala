import cake.MetadataModelComponent
import com.borachio.scalatest.MockFactory
import eu.delving.metadata.MetadataModel
import models.{PortalTheme, User}
import org.scalatest.matchers._
import org.scalatest.Suite
import play.test._
import util.{ThemeHandler, ThemeHandlerComponent}

trait TestEnvironment extends ThemeHandlerComponent with MetadataModelComponent with Suite with MockFactory {
  val metadataModel: MetadataModel = mock[MetadataModel]
  val themeHandler: ThemeHandler
}

trait TestDataUsers {
  Yaml[List[Any]]("testUsers.yml").foreach {
    _ match {
      case u: User => User.insert(u)
    }
  }
}

class ThemeHandlerTests extends UnitFlatSpec with ShouldMatchers with TestDataUsers with TestEnvironment {

  val themeHandler = new ThemeHandler

  it should "load themes from disk" in {
    themeHandler.startup()
    themeHandler.hasSingleTheme should be(false)
    PortalTheme.findAll.size should not be (0)
  }

}