import core.HubModule
import test.Specs2TestContext
import play.api.test._
import play.api.test.Helpers._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class ApplicationControllerSpec extends Specs2TestContext {

  "The legacy redirection mechanism" should {

    val controller = new controllers.Application()(HubModule)

    "permanently redirect legacy API calls" in {
      val result = controller.legacyOrganizationsPath("api/oai-pmh/format")(FakeRequest())
      status(result) must equalTo(301)
      redirectLocation(result) must beSome("/api/oai-pmh/format")
    }

    "permanently redirect legacy legacy proxy API calls" in {
      val result = controller.legacyOrganizationsPath("proxy/fooKey/search")(FakeRequest())
      status(result) must equalTo(301)
      redirectLocation(result) must beSome("/api/proxy/fooKey/search")

      val result1 = controller.legacyOrganizationsPath("proxy/fooKey/item/key/with/slashes")(FakeRequest())
      status(result1) must equalTo(301)
      redirectLocation(result1) must beSome("/api/proxy/fooKey/item/key/with/slashes")
    }

    "permanently redirect legacy organization administration calls" in {
      val result = controller.legacyOrganizationsPath("dataset/list")(FakeRequest())
      status(result) must equalTo(301)
      redirectLocation(result) must beSome("/admin/dataset/list")
    }

    "permanently redirect legacy organization root calls" in {
      val result = controller.legacyOrganizationsPath("root")(FakeRequest())
      status(result) must equalTo(301)
      redirectLocation(result) must beSome("/admin")
    }

  }

}
