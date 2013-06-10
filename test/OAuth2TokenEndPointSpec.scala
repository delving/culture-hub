import core.HubModule
import org.apache.amber.oauth2.client.request.OAuthClientRequest
import org.apache.amber.oauth2.client.response.{ OAuthClientResponseFactory, OAuthJSONAccessTokenResponse }
import org.apache.amber.oauth2.common.message.types.GrantType
import org.specs2.mutable._
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.mvc.Results.EmptyContent
import play.api.mvc.{ AnyContentAsEmpty, AnyContent, AnyContentAsText }
import play.api.test._
import play.api.test.Helpers._
import test.Specs2TestContext

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class OAuth2TokenEndPointSpec extends Specs2TestContext {

  "the OAuth2 token end-point" should {

    val controller = new controllers.OAuth2TokenEndpoint()(HubModule)

    "accept a password grant request" in {

      withTestData() {

        val endPoint = "http://delving.localhost:9000/token"

        val request: OAuthClientRequest = OAuthClientRequest.
          tokenLocation(endPoint).
          setGrantType(GrantType.PASSWORD).
          setUsername("bob").
          setPassword("secret").
          buildQueryMessage()

        val fakeRequest = new FakeRequest[AnyContent](
          method = "GET",
          body = AnyContentAsText(request.getBody),
          uri = request.getLocationUri,
          headers = FakeHeaders()
        )

        val resp = controller.token()(fakeRequest)

        status(resp) must equalTo(OK)

        val content = contentAsString(resp)
        val contentType = headers(resp).get(CONTENT_TYPE)

        val oNTokenResponse = OAuthClientResponseFactory.createJSONTokenResponse(content, contentType.get, status(resp)).asInstanceOf[OAuthJSONAccessTokenResponse]

        oNTokenResponse.getAccessToken must not equalTo null
        oNTokenResponse.getExpiresIn must equalTo(3600l)
        oNTokenResponse.getRefreshToken must not equalTo null
      }

    }

    "reject a password grant request" in {

      withTestData() {

        val endPoint = "http://delving.localhost:9000/token"

        val request: OAuthClientRequest = OAuthClientRequest.
          tokenLocation(endPoint).
          setGrantType(GrantType.PASSWORD).
          setUsername("some").
          setPassword("dude").
          buildQueryMessage()

        val fakeRequest = new FakeRequest[AnyContent](
          method = "GET",
          body = AnyContentAsText(request.getBody),
          uri = request.getLocationUri,
          headers = FakeHeaders()
        )

        val resp = controller.token()(fakeRequest)

        status(resp) must equalTo(BAD_REQUEST)

        val content = contentAsString(resp)

        val p = Json.parse(content)

        (p \ "error").as[String] must equalTo("invalid_grant")
        (p \ "error_description").as[String] must equalTo("invalid username or password")
      }
    }
  }

}