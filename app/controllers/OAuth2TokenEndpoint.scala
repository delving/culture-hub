package controllers

import org.apache.amber.oauth2.as.issuer.{ MD5Generator, OAuthIssuerImpl, OAuthIssuer }
import org.apache.amber.oauth2.common.OAuth
import org.apache.amber.oauth2.common.message.OAuthResponse
import org.apache.amber.oauth2.as.response.OAuthASResponse
import org.apache.amber.oauth2.common.error.OAuthError
import org.apache.amber.oauth2.common.exception.OAuthProblemException
import org.apache.amber.oauth2.common.validators.OAuthValidator
import org.apache.amber.oauth2.common.message.types.GrantType
import org.apache.amber.oauth2.common.utils.OAuthUtils
import org.apache.amber.oauth2.as.request.OAuthRequest
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import org.apache.amber.oauth2.as.validator._
import play.api._
import play.api.Play.current
import mvc._
import core.{ AuthenticationService, DomainServiceLocator }
import models.HubUser
import scala.Left
import scala.Right
import com.escalatesoft.subcut.inject.BindingModule

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
class OAuth2TokenEndpoint(implicit val bindingModule: BindingModule) extends ApplicationController {

  val authenticationServiceLocator = inject[DomainServiceLocator[AuthenticationService]]

  def token: Action[AnyContent] = OrganizationConfigured {
    implicit request =>
      val oauthIssuerImpl: OAuthIssuer = new OAuthIssuerImpl(new MD5Generator)

      try {
        val oauthRequest = new PlayOAuthTokenRequest(request)

        // see http://tools.ietf.org/html/draft-ietf-oauth-v2-18#section-4.4.1
        if (oauthRequest.getGrantType == null) {
          errorResponse(OAuthError.TokenResponse.INVALID_REQUEST, "no grant_type provided")
        } else {
          try {
            val grantType = GrantType.valueOf(oauthRequest.getGrantType.toUpperCase)
            val mayUser = grantType match {
              // TODO use real node from URL
              case GrantType.PASSWORD =>
                if (!authenticationServiceLocator.byDomain.connect(oauthRequest.getUsername, oauthRequest.getPassword)) {
                  Left(errorResponse(OAuthError.TokenResponse.INVALID_GRANT, "invalid username or password"))
                } else {
                  Right(HubUser.dao.findByUsername(oauthRequest.getUsername).get)
                }
              case GrantType.REFRESH_TOKEN => {
                val maybeUser = HubUser.dao.findByRefreshToken(oauthRequest.getParam(OAuth.OAUTH_REFRESH_TOKEN))
                if (maybeUser == None) {
                  Left(errorResponse(OAuthError.ResourceResponse.INVALID_TOKEN, "Invalid refresh token"))
                } else {
                  Right(maybeUser.get)
                }
              }
              // TODO
              case GrantType.AUTHORIZATION_CODE | GrantType.CLIENT_CREDENTIALS => Left(errorResponse(OAuthError.TokenResponse.UNSUPPORTED_GRANT_TYPE, "unsupported grant type"))
            }

            if (mayUser.isLeft) {
              mayUser.left.get
            } else {
              val user = mayUser.right.get

              var accessToken: String = null
              var refreshToken: String = null

              if (grantType == GrantType.REFRESH_TOKEN) {
                accessToken = oauthIssuerImpl.accessToken
                refreshToken = oauthIssuerImpl.refreshToken
                HubUser.dao.setOauthTokens(user, accessToken, refreshToken)
              } else {
                accessToken = if (user.accessToken != None) user.accessToken.get.token else oauthIssuerImpl.accessToken
                refreshToken = if (user.refreshToken != None) user.refreshToken.get else oauthIssuerImpl.refreshToken
                // save only if this is new
                if (user.accessToken == None) {
                  HubUser.dao.setOauthTokens(user, accessToken, refreshToken)
                }
              }

              val resp: OAuthResponse = OAuthASResponse.tokenResponse(HttpServletResponse.SC_OK).setAccessToken(accessToken).setRefreshToken(refreshToken).setExpiresIn(HubUser.OAUTH2_TOKEN_TIMEOUT.toString).buildJSONMessage()
              WrappedJson(resp.getBody)

            }
          } catch {
            case e: OAuthProblemException => {
              val builder = new OAuthResponse.OAuthErrorResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
              val resp: OAuthResponse = builder.error(e).buildJSONMessage()
              Logger("CultureHub").warn(resp.getBody)
              BadRequest(resp.getBody).as(JSON)
            }
          }

        }
      } catch {
        case iae: IllegalArgumentException => errorResponse(OAuthError.TokenResponse.INVALID_REQUEST, "invalid grant_type provided")
      }

  }

  def errorResponse(tokenResponse: String, message: String)(implicit request: RequestHeader) = {
    val builder = new OAuthResponse.OAuthErrorResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
    val resp: OAuthResponse = builder.setError(tokenResponse).setErrorDescription(message).buildJSONMessage()
    Logger("CultureHub").warn(resp.getBody)
    BadRequest(resp.getBody).as(JSON)
  }

  /**ensure that some content is set, so that there will always be a Content-Length in the response **/
  def WrappedJson(payload: String) = if (payload == null) Ok("").as(JSON) else Ok(payload).as(JSON)

}

/**
 * Play wrapper for the OAuth token request
 */
class PlayOAuthTokenRequest(request: RequestHeader) extends OAuthRequest {

  def initValidator() = {
    validators.put(GrantType.PASSWORD.toString, classOf[PasswordValidator])
    validators.put(GrantType.AUTHORIZATION_CODE.toString, classOf[AuthorizationCodeValidator])
    validators.put(GrantType.REFRESH_TOKEN.toString, classOf[RefreshTokenValidator])
    val requestTypeValue: String = getParam(OAuth.OAUTH_GRANT_TYPE)
    if (OAuthUtils.isEmpty(requestTypeValue)) {
      throw OAuthUtils.handleOAuthProblemException("Missing grant_type parameter value")
    }
    val clazz = validators.get(requestTypeValue)
    if (clazz == null) {
      throw OAuthUtils.handleOAuthProblemException("Invalid grant_type parameter value")
    }
    OAuthUtils.instantiateClass(clazz)
  }

  override def getParam(name: String) = request.queryString.get(name).getOrElse(Seq("")).head

  def getPassword = getParam(OAuth.OAUTH_PASSWORD)

  def getUsername = getParam(OAuth.OAUTH_USERNAME)

  def getAssertion = getParam(OAuth.OAUTH_ASSERTION)

  def getAssertionType = getParam(OAuth.OAUTH_ASSERTION_TYPE)

  def getCode = getParam(OAuth.OAUTH_CODE)

  def getGrantType = getParam(OAuth.OAUTH_GRANT_TYPE)

  def getState = getParam(OAuth.OAUTH_STATE)
}