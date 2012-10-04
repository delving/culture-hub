import models.VirtualNode
import play.api.libs.json.{JsString, JsObject}
import play.api.mvc.AnyContentAsJson
import play.api.test._
import play.api.test.Helpers._


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

        VirtualNode.dao("delving").findOne("delving", "rotterdam") must beSome
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
        val node = VirtualNode.dao("delving").findOne("delving", "rotterdam").get
        val response = controllers.organization.VirtualNodes.delete(node._id)(
          FakeRequest(method = "DELETE", path = "/organizations/delving/node/delete/" + node._id.toString).withSession(
            ("userName" -> "bob")
          )
        )
        status(response) must equalTo(200)
        VirtualNode.dao("delving").findOne("delving", "rotterdam") must beNone
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
          "nodeId" -> JsString("rotterdam"),
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
