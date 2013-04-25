import controllers.mediator.MediatorServer
import models.OrganizationConfiguration
import play.api.test._
import play.api.test.Helpers._
import test.Specs2TestContext

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class MediatorServerSpec extends Specs2TestContext {

  "The Mediator Server" should {

    "return 404 if an image was not found" in {
      withTestConfig { implicit configuration: OrganizationConfiguration =>
        val result = MediatorServer.newFile("delving", "fooSet", "fooFile.jpg", s"http://${configuration.domains.head}:9000/media/fault/newFile")(FakeRequest())
        status(result) must equalTo(404)
      }
    }

  }

}
