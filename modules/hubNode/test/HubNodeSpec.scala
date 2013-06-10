import core.HubModule
import core.services.AggregatingNodeSubscriptionService
import models.HubNode
import play.api.libs.json.{ JsString, JsObject }
import play.api.mvc.AnyContentAsJson
import play.api.test._
import play.api.test.Helpers._
import test.Specs2TestContext
import util.OrganizationConfigurationHandler

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class HubNodeSpec extends test.Specs2TestContext {

  val controller = new controllers.organization.HubNodes()(HubModule)

  step {
    loadStandalone()
  }

  "The HubNode controller" should {

    "create a new node" in {

      withTestConfig {

        val response = controller.submit(fakeRequest)
        status(response) must equalTo(200)

        HubNode.dao("delving").findOne("rotterdam-node") must beSome
      }

    }

    "find a created node" in {

      withTestConfig {

        implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")

        val node = HubNode.dao.findOne("rotterdam-node").get

        val broadcastingNodeSubscriptionService = new AggregatingNodeSubscriptionService

        broadcastingNodeSubscriptionService.listActiveSubscriptions(node) must equalTo(Seq(configuration.node))
        broadcastingNodeSubscriptionService.listActiveSubscriptions(configuration.node) must equalTo(Seq(node))
      }

    }

    "not allow to create two nodes with the same ID" in {

      withTestConfig {
        val response = controller.submit(fakeRequest)
        status(response) must equalTo(400)
      }

    }

    "delete a node" in {

      withTestConfig {
        val node = HubNode.dao("delving").findOne("rotterdam-node").get
        val response = controller.delete(node._id)(
          FakeRequest(method = "DELETE", path = "/admin/hubNode/delete/" + node._id.toString).withSession(
            ("userName" -> "bob")
          )
        )
        status(response) must equalTo(200)
        HubNode.dao("delving").findOne("rotterdam") must beNone
      }

    }

  }

  def fakeRequest = FakeRequest(
    method = "POST",
    uri = "",
    headers = FakeHeaders(),
    body = AnyContentAsJson(
      JsObject(
        Seq(
          "nodeId" -> JsString("rotterdam-node"),
          "orgId" -> JsString("delving"),
          "name" -> JsString("Rotterdam Node")
        )
      )
    )
  ).withSession(
      ("userName" -> "bob")
    )

  step {
    cleanup(cleanupSOLR = false)
  }

}