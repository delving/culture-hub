import controllers.mediator.MediatorServer
import play.api.test._
import play.api.test.Helpers._
import test.Specs2TestContext
import util.OrganizationConfigurationHandler

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class MediatorServerSpec extends Specs2TestContext {

  "The Mediator Server" should {

    "return 404 if an image was not found" in {
      withTestConfig {
        implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")
        val result = MediatorServer.newFile("delving", "fooSet", "fooFile.jpg", s"http://${configuration.domains.head}:9000/media/event/fileHandled")(FakeRequest())
        status(result) must equalTo(404)
      }
    }

  }

}
