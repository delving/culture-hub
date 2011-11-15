import org.apache.amber.oauth2.as.request.OAuthTokenRequest
import org.apache.amber.oauth2.client.request.OAuthClientRequest
import org.apache.amber.oauth2.client.response.{OAuthClientResponse, OAuthClientResponseFactory, OAuthJSONAccessTokenResponse}
import org.apache.amber.oauth2.common.message.types.GrantType
import org.scalatest.matchers.ShouldMatchers
import play.libs.WS
import play.test.UnitFlatSpec
import util.TestDataGeneric
import scala.collection.JavaConversions._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class OAuth2TokenEndPointSpec extends UnitFlatSpec with ShouldMatchers with TestDataGeneric {

  it should "accept a password grant request" in {
    val endPoint = "http://localhost:9000/token"

    val request: OAuthClientRequest = OAuthClientRequest.
            tokenLocation(endPoint).
            setGrantType(GrantType.PASSWORD).
            setUsername("bob").
            setPassword("secret").
            buildQueryMessage()

    val req = WS.url(request.getLocationUri)
    // wtf
    if(request.getHeaders != null) {
      request.getHeaders foreach {
        h => req.setHeader(h._1, h._2)
      }
    }

    val resp: WS.HttpResponse = req.get()
    resp.getStatus should be(200)

    val oNTokenResponse = OAuthClientResponseFactory.createJSONTokenResponse(resp.getString, resp.getHeaders().filter(h => h.name.equalsIgnoreCase("content-type")).head.value(), resp.getStatus.intValue()).asInstanceOf[OAuthJSONAccessTokenResponse]

    oNTokenResponse.getAccessToken should not be (null)
    oNTokenResponse.getExpiresIn should be (3600)
    oNTokenResponse.getRefreshToken should not be (null)

  }

}