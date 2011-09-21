package controllers

import org.apache.amber.oauth2.as.issuer.{MD5Generator, OAuthIssuerImpl, OAuthIssuer}
import org.apache.amber.oauth2.common.OAuth
import org.apache.amber.oauth2.common.message.OAuthResponse
import org.apache.amber.oauth2.as.response.OAuthASResponse
import org.apache.amber.oauth2.common.error.OAuthError
import org.apache.amber.oauth2.common.exception.OAuthProblemException
import org.apache.amber.oauth2.common.validators.OAuthValidator
import org.apache.amber.oauth2.common.message.types.GrantType
import org.apache.amber.oauth2.common.utils.OAuthUtils
import org.apache.amber.oauth2.as.request.OAuthRequest
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.amber.oauth2.as.validator._
import play.mvc.results.Result
import play.mvc.{Util, Controller, Http}
import models.User


/**
 * OAuth2 TokenEndPoint inspired by the Apache Amber examples and the RFC draft 10
 *
 * TODO the spec has the concept of expired tokens, which means we can't simply evict expired access tokens but need to keep them someplace in order to be able to tell the client that the token is expired.
 *
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-18
 * @see https://svn.apache.org/repos/asf/incubator/amber/trunk/oauth-2.0/oauth2-integration-tests/src/test/java/org/apache/amber/oauth2/integration/endpoints/TokenEndpoint.java
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object OAuth2TokenEndpoint extends Controller {

  val TOKEN_TIMEOUT = 3600

  val security = new ServicesSecurity

  def token(): Result = {
    val oauthIssuerImpl: OAuthIssuer = new OAuthIssuerImpl(new MD5Generator)

    try {
      val oauthRequest = new PlayOAuthTokenRequest(request)

      // see http://tools.ietf.org/html/draft-ietf-oauth-v2-18#section-4.4.1
      if (oauthRequest.getGrantType == null) return errorResponse(OAuthError.TokenResponse.INVALID_REQUEST, "no grant_type provided")

      var grantType: GrantType = null;
      try {
        grantType = GrantType.valueOf(oauthRequest.getGrantType.toUpperCase)
      } catch {
        case iae: IllegalArgumentException => return errorResponse(OAuthError.TokenResponse.INVALID_REQUEST, "invalid grant_type provided")
      }

      val user = grantType match {
        // TODO use real node from URL
        case GrantType.PASSWORD => if (!security.authenticate(oauthRequest.getUsername, oauthRequest.getPassword)) return errorResponse(OAuthError.TokenResponse.INVALID_GRANT, "invalid username or password") else User.findByUserId(oauthRequest.getUsername + "#cultureHub").get
        case GrantType.REFRESH_TOKEN => {
          val maybeUser = User.findByRefreshToken(oauthRequest.getRefreshToken)
          if(maybeUser == None) {
             return errorResponse(OAuthError.ResourceResponse.INVALID_TOKEN, "Invalid refresh token")
          } else {
            maybeUser.get
          }
        }
        // TODO
        case GrantType.AUTHORIZATION_CODE => return errorResponse(OAuthError.TokenResponse.UNSUPPORTED_GRANT_TYPE, "unsupported grant type")
        case GrantType.ASSERTION => return errorResponse(OAuthError.TokenResponse.UNSUPPORTED_GRANT_TYPE, "unsupported grant type")
        case GrantType.NONE => return errorResponse(OAuthError.TokenResponse.UNSUPPORTED_GRANT_TYPE, "unsupported grant type")
      }

      var accessToken: String = null;
      var refreshToken: String = null;

      if (grantType == GrantType.REFRESH_TOKEN) {
        accessToken = oauthIssuerImpl.accessToken
        refreshToken = oauthIssuerImpl.refreshToken
        User.setOauthTokens(user, accessToken, refreshToken)
      } else {
        accessToken = if(user.accessToken != None) user.accessToken.get.token else oauthIssuerImpl.accessToken
        refreshToken = if(user.refreshToken != None) user.refreshToken.get else oauthIssuerImpl.refreshToken
        // save only if this is new
        if(user.accessToken == None) {
          User.setOauthTokens(user, accessToken, refreshToken)
        }
      }

      val resp: OAuthResponse = OAuthASResponse.tokenResponse(HttpServletResponse.SC_OK).setAccessToken(accessToken).setRefreshToken(refreshToken).setExpiresIn(TOKEN_TIMEOUT.toString).buildJSONMessage()
      Json(resp.getBody)
    }
    catch {
      case e: OAuthProblemException => {
        val builder = new OAuthResponse.OAuthErrorResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
        val resp: OAuthResponse = builder.error(e).buildJSONMessage()
        response.status = 400
        Json(resp.getBody)
      }
    }
  }

  def errorResponse(tokenResponse: String, message: String): Result = {
    val builder = new OAuthResponse.OAuthErrorResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
    val resp: OAuthResponse = builder.setError(tokenResponse).setErrorDescription(message).buildJSONMessage()
    response.status = 400
    Json(resp.getBody)
  }

  @Util def isValidToken(token: String): Boolean = {
    if(play.Play.mode == play.Play.Mode.DEV && token == "TEST") return true
    User.isValidAccessToken(token, TOKEN_TIMEOUT)
  }

  @Util def evictExpiredTokens() {
    User.evictExpiredAccessTokens(TOKEN_TIMEOUT)
  }

  @Util def getUserByToken(token: String) = User.findByAccessToken(token)

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