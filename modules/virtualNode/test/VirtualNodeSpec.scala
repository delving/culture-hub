import core.services.BroadcastingNodeSubscriptionService
import models.VirtualNode
import play.api.libs.json.{JsString, JsObject}
import play.api.mvc.AnyContentAsJson
import play.api.test._
import play.api.test.Helpers._
import util.DomainConfigurationHandler


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class VirtualNodeSpec extends Specs2TestContext {

  step {
    loadStandalone()
  }

  "The VirtualNode controller" should {

    "create a new node" in {

      withTestConfig {

        val response = controllers.organization.VirtualNodes.submit(fakeRequest)
        status(response) must equalTo(200)

        VirtualNode.dao("delving").findOne("rotterdam-node") must beSome
      }

    }

    "find a created node" in {

      withTestConfig {

        implicit val configuration = DomainConfigurationHandler.getByOrgId("delving")

        val node = VirtualNode.dao.findOne("rotterdam-node").get

        val broadcastingNodeSubscriptionService = new BroadcastingNodeSubscriptionService

        broadcastingNodeSubscriptionService.listActiveSubscriptions(node) must equalTo(Seq(configuration.node))
        broadcastingNodeSubscriptionService.listActiveSubscriptions(configuration.node) must equalTo(Seq(node))
      }

    }

    "not allow to create two nodes with the same ID" in {

      withTestConfig {
        val response = controllers.organization.VirtualNodes.submit(fakeRequest)
        status(response) must equalTo(400)
      }

    }

    "delete a node" in {

      withTestConfig {
        val node = VirtualNode.dao("delving").findOne("rotterdam-node").get
        val response = controllers.organization.VirtualNodes.delete(node._id)(
          FakeRequest(method = "DELETE", path = "/organizations/delving/node/delete/" + node._id.toString).withSession(
            ("userName" -> "bob")
          )
        )
        status(response) must equalTo(200)
        VirtualNode.dao("delving").findOne("rotterdam") must beNone
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
    cleanup()
  }

}
