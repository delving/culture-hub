import cake.MetadataModelComponent
import eu.delving.metadata.MetadataModel
import models.{User}
import org.scalatest.matchers._
import play.test._
import org.mockito.Mockito._

trait TestEnvironment extends MetadataModelComponent {
  val metadataModel: MetadataModel = mock(classOf[MetadataModel])
//  val themeHandler: ThemeHandler = mock(classOf[ThemeHandler])
}

trait TestDataUsers {
  Yaml[List[Any]]("testUsers.yml").foreach {
    _ match {
      case u: User => User.insert(u)
    }
  }
}

class ThemeHandlerTests extends UnitFlatSpec with ShouldMatchers with TestDataUsers {

  it should "run this dumb test" in {
    (1 + 1) should be(2)
  }


  it should "load themes from disk" in {
//    Foo.themeHandler.startup()
//    Foo.themeHandler.hasSingleTheme should be(false)
//    PortalTheme.findAll.size should not be (0)
  }

}

object Foo extends TestEnvironment {
//  override val themeHandler = new ThemeHandler


}