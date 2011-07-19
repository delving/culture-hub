package controllers

import org.apache.amber.oauth2.as.issuer.{MD5Generator, OAuthIssuerImpl, OAuthIssuer}
import org.apache.amber.oauth2.common.OAuth
import org.apache.amber.oauth2.common.message.OAuthResponse
import org.apache.amber.oauth2.as.response.OAuthASResponse
import org.apache.amber.oauth2.common.error.OAuthError
import org.apache.amber.oauth2.common.exception.OAuthProblemException
import play.mvc.results.Result
import extensions.RenderLiftJson
import play.mvc.Http
import org.apache.amber.oauth2.common.validators.OAuthValidator
import org.apache.amber.oauth2.common.message.types.GrantType
import org.apache.amber.oauth2.common.utils.OAuthUtils
import org.apache.amber.oauth2.as.request.OAuthRequest
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.amber.oauth2.as.validator._

/**
 * TokenEndPoint inspired from the Apache Amber examples
 *
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-18
 * @see https://svn.apache.org/repos/asf/incubator/amber/trunk/oauth-2.0/oauth2-integration-tests/src/test/java/org/apache/amber/oauth2/integration/endpoints/TokenEndpoint.java
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class OAuth2TokenEndpoint extends DelvingController {

  val security = new ServicesSecurity

  def token(): Result = {

    val oauthIssuerImpl: OAuthIssuer = new OAuthIssuerImpl(new MD5Generator)
    try {
      val oauthRequest = new PlayOAuthTokenRequest(request)

      if (GrantType.ASSERTION.toString != oauthRequest.getGrantType) {
        /* TODO
        if (!(Common.CLIENT_ID == oauthRequest.getParam(OAuth.OAUTH_CLIENT_ID))) {
          var response: OAuthResponse = OAuthASResponse.errorResponse(HttpServletResponse.SC_BAD_REQUEST).setError(OAuthError.TokenResponse.INVALID_CLIENT).setErrorDescription("client_id not found").buildJSONMessage
          return Response.status(response.getResponseStatus).entity(response.getBody).build
        }
        */
      }
      if (oauthRequest.getParam(OAuth.OAUTH_GRANT_TYPE) == GrantType.AUTHORIZATION_CODE.toString) {
        if (!("foo" == oauthRequest.getParam(OAuth.OAUTH_CODE))) errorResponse(OAuthError.TokenResponse.INVALID_GRANT, "invalid authorization code")
      } else if (oauthRequest.getParam(OAuth.OAUTH_GRANT_TYPE) == GrantType.PASSWORD.toString) {
        if (!security.authenticate(oauthRequest.getUsername, oauthRequest.getPassword)) errorResponse(OAuthError.TokenResponse.INVALID_GRANT, "invalid username or password")

      } else if (oauthRequest.getParam(OAuth.OAUTH_GRANT_TYPE) == GrantType.ASSERTION.toString) {
        // not yet supported
        // if (!(Common.ASSERTION == oauthRequest.getAssertion)) {
        errorResponse(OAuthError.TokenResponse.UNSUPPORTED_GRANT_TYPE, "unsupported grant type")
      } else if (oauthRequest.getParam(OAuth.OAUTH_GRANT_TYPE) == GrantType.REFRESH_TOKEN.toString) {
        errorResponse(OAuthError.TokenResponse.INVALID_GRANT, "invalid username or password")
      }

      // we respond if all the above is ok
      val response: OAuthResponse = OAuthASResponse.tokenResponse(HttpServletResponse.SC_OK).setAccessToken(oauthIssuerImpl.accessToken).setExpiresIn("3600").buildJSONMessage
      new RenderLiftJson(response.getBody, HttpServletResponse.SC_OK)
    }
    catch {
      case e: OAuthProblemException => {
        val builder = new OAuthResponse.OAuthErrorResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
        builder.error(e).buildJSONMessage
        new RenderLiftJson(builder.buildJSONMessage().getBody, HttpServletResponse.SC_BAD_REQUEST)
      }
    }
    Ok
  }

  def errorResponse(tokenResponse: String, message: String): Result = {
    val builder = new OAuthResponse.OAuthErrorResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
    builder.setError(tokenResponse).setErrorDescription(message).buildJSONMessage
    new RenderLiftJson(builder.buildJSONMessage().getBody, HttpServletResponse.SC_BAD_REQUEST)
  }

  /**
   * Play wrapper for the OAuth token request
   */
  class PlayOAuthTokenRequest(request: Http.Request) extends OAuthRequest {

    def initValidator() = {
      validators.put(GrantType.PASSWORD.toString, classOf[PasswordValidator])
      validators.put(GrantType.ASSERTION.toString, classOf[AssertionValidator])
      validators.put(GrantType.AUTHORIZATION_CODE.toString, classOf[AuthorizationCodeValidator])
      validators.put(GrantType.REFRESH_TOKEN.toString, classOf[RefreshTokenValidator])
      val requestTypeValue: String = getParam(OAuth.OAUTH_GRANT_TYPE).asInstanceOf[String]
      if (OAuthUtils.isEmpty(requestTypeValue)) {
        throw OAuthUtils.handleOAuthProblemException("Missing grant_type parameter value")
      }
      val clazz = validators.get(requestTypeValue);
      if (clazz == null) {
        throw OAuthUtils.handleOAuthProblemException("Invalid grant_type parameter value")
      }
      (OAuthUtils.instantiateClass(clazz)).asInstanceOf[OAuthValidator[HttpServletRequest]];
    }

    override def getParam(name: String) = request.params.get(name)

    def getPassword = getParam(OAuth.OAUTH_PASSWORD)

    def getUsername = getParam(OAuth.OAUTH_USERNAME)

    def getAssertion = getParam(OAuth.OAUTH_ASSERTION)

    def getAssertionType = getParam(OAuth.OAUTH_ASSERTION_TYPE)

    def getCode = getParam(OAuth.OAUTH_CODE)

    def getGrantType = getParam(OAuth.OAUTH_GRANT_TYPE)

    def getState = getParam(OAuth.OAUTH_STATE)
  }

}