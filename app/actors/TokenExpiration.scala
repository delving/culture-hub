package actors

import akka.actor.Actor
import controllers.OAuth2TokenEndpoint

/**
 * Authentication stuff.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class TokenExpiration extends Actor {

  protected def receive = {
    case EvictOAuth2Tokens => OAuth2TokenEndpoint.evictExpiredTokens()
  }
}

case class EvictOAuth2Tokens()
