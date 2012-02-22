package actors

import akka.actor.Actor
import controllers.OAuth2TokenEndpoint
import models.User

/**
 * Authentication stuff.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class TokenExpiration extends Actor {

  protected def receive = {

    case EvictPasswordResetTokens => User.evictExpiredPasswordResetTokens()
    case EvictOAuth2Tokens => OAuth2TokenEndpoint.evictExpiredTokens()

  }
}

case class EvictPasswordResetTokens()

case class EvictOAuth2Tokens()
