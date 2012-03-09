import org.apache.amber.oauth2.client.request.OAuthClientRequest
import org.apache.amber.oauth2.client.response.{OAuthClientResponseFactory, OAuthJSONAccessTokenResponse}
import org.apache.amber.oauth2.common.message.types.GrantType
import org.specs2.mutable._
import play.api.libs.ws.WS
import play.api.test._
import play.api.test.Helpers._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class OAuth2TokenEndPointSpec extends Specification with Cleanup {

  "the OAuth2 token end-point" should {
    "accept a password grant request" in {
      val endPoint = "http://localhost:9000/token"

      val request: OAuthClientRequest = OAuthClientRequest.
        tokenLocation(endPoint).
        setGrantType(GrantType.PASSWORD).
        setUsername("bob").
        setPassword("secret").
        buildQueryMessage()

      val resp = WS.url(request.getLocationUri).get()

      val r = resp.await.get
      r.status must equalTo (OK)

      val oNTokenResponse = OAuthClientResponseFactory.createJSONTokenResponse(r.body, r.header(CONTENT_TYPE).get, r.status).asInstanceOf[OAuthJSONAccessTokenResponse]

      oNTokenResponse.getAccessToken must not equalTo (null)
      oNTokenResponse.getExpiresIn must be("3600")
      oNTokenResponse.getRefreshToken must not equalTo (null)
    }

  }


}