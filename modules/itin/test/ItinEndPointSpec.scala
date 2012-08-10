import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import play.api.Play
import play.api.Play.current
import xml.XML

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 3/15/12 8:40 AM  
 */

class ItinEndPointSpec extends TestContext {

  "ItinEndPoint" should {

    "Store XML post" in {

      withTestConfig {

        val fakeRequest: FakeRequest[scala.xml.NodeSeq] = FakeRequest(
          method = "POST",
          uri = "",
          headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("application/xml"))),
          body = XML.load(Play.application.resourceAsStream("/xmlpost1320334216.xml").get)
        )

        val result = asyncToResult(controllers.itin.ItinEndPoint.store()(fakeRequest))
        status(result) must equalTo(OK)

        contentAsString(result) must contain("success")
      }

    }
  }

  step(cleanup)

}
