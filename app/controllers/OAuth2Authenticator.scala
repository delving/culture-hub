package controllers

import org.apache.amber.oauth2.as.request.OAuthRequest
import org.apache.amber.oauth2.common.validators.OAuthValidator
import org.apache.amber.oauth2.common.utils.OAuthUtils
import org.apache.amber.oauth2.common.message.types.ResponseType
import org.apache.amber.oauth2.as.validator.{CodeTokenValidator, TokenValidator, CodeValidator}
import org.apache.amber.oauth2.common.OAuth
import org.apache.amber.oauth2.as.issuer.{MD5Generator, OAuthIssuerImpl}
import org.apache.amber.oauth2.as.response.OAuthASResponse
import org.apache.amber.oauth2.common.message.OAuthResponse.OAuthErrorResponseBuilder
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import play.api.mvc._
import core.{AuthenticationService, DomainServiceLocator, HubModule}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object OAuth2Authenticator extends BoundController(HubModule) with OAuth2Authenticator

trait OAuth2Authenticator extends Controller with OrganizationConfigurationAware { this: BoundController with Controller with OrganizationConfigurationAware =>

  val authenticationServiceLocator = inject [ DomainServiceLocator[AuthenticationService] ]

  def authenticate = OrganizationConfigured {
    Action {
      implicit request =>

        val oauthRequest = new PlayOAuthAuthzRequest(request)
        val responseType = oauthRequest.getParam(OAuth.OAUTH_RESPONSE_TYPE)
        val authenticated: Boolean = authenticationServiceLocator.byDomain.connect(oauthRequest.getClientId, oauthRequest.getClientSecret)

        if(authenticated) {
          val oauthIssuerImpl = new OAuthIssuerImpl(new MD5Generator)
          val builder = OAuthASResponse.authorizationResponse(HttpServletResponse.SC_FOUND)

          if (responseType == ResponseType.CODE.toString || responseType == ResponseType.CODE_AND_TOKEN.toString) {
            builder.setCode(oauthIssuerImpl.authorizationCode());
          }
          if (responseType.equals(ResponseType.TOKEN.toString) || responseType == ResponseType.CODE_AND_TOKEN.toString) {
            builder.setAccessToken(oauthIssuerImpl.accessToken());
            builder.setExpiresIn(String.valueOf(3600));
          }

          val redirectURI = oauthRequest.getParam(OAuth.OAUTH_REDIRECT_URI)
          val response = builder.location(redirectURI).buildQueryMessage();
          Redirect(response.getLocationUri)
        } else {
          val builder = new OAuthErrorResponseBuilder(HttpServletResponse.SC_UNAUTHORIZED)
          // http://tools.ietf.org/html/draft-ietf-oauth-v2-18#section-4.1.2.1
          builder.setError("access_denied").setErrorDescription("Access denied")

          // TODO check if this works
          Unauthorized(builder.buildBodyMessage().getBody)
        }
    }
  }

  }


/**
 * Play wrapper for OAuth authentication request
 */
class PlayOAuthAuthzRequest(request: RequestHeader) extends OAuthRequest {

  def initValidator() = {
    //end user authorization validators
    validators.put(ResponseType.CODE.toString, classOf[CodeValidator])
    validators.put(ResponseType.TOKEN.toString, classOf[TokenValidator])
    validators.put(ResponseType.CODE_AND_TOKEN.toString, classOf[CodeTokenValidator])
    val requestTypeValue = getParam(OAuth.OAUTH_RESPONSE_TYPE);
    if (OAuthUtils.isEmpty(requestTypeValue)) {
      throw OAuthUtils.handleOAuthProblemException("Missing response_type parameter value")
    }
    val clazz = validators.get(requestTypeValue);
    if (clazz == null) {
      throw OAuthUtils.handleOAuthProblemException("Invalid response_type parameter value")
    }
    (OAuthUtils.instantiateClass(clazz)).asInstanceOf[OAuthValidator[HttpServletRequest]];
  }

  override def getParam(name: String) = request.queryString.get(name).getOrElse(Seq("")).head

  def getState = getParam(OAuth.OAUTH_STATE)
}
