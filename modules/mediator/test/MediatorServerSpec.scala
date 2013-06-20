import core.HubModule
import java.io.File
import models.OrganizationConfiguration
import play.api.test._
import play.api.test.Helpers._
import test.Specs2TestContext

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class MediatorServerSpec extends Specs2TestContext {

  val controller = new controllers.mediator.MediatorServer()(HubModule)

  "The Mediator Server" should {

    "return 404 if an image was not found" in {
      withTestConfig { implicit configuration: OrganizationConfiguration =>
        val result = controller.newFile("delving", "fooSet", "fooFile.jpg", "bob", s"http://${configuration.domains.head}:9000/media/fault/newFile")(FakeRequest())
        status(result) must equalTo(404)
      }
    }
    "return 400 if a file is not an image" in {
      val here = new File("modules/mediator/test/resources")
      withTestConfig(Map("configurations.delving.plugin.mediator.sourceDirectory" -> here.getAbsolutePath)) { implicit configuration: OrganizationConfiguration =>
        val result = controller.newFile("delving", "testSpec", "dummy.txt", "bob", s"http://${configuration.domains.head}:9000/media/fault/newFile")(FakeRequest())
        status(result) must equalTo(400)
      }
    }

  }

}