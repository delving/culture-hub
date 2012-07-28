package actors

import akka.actor.Actor
import controllers.OAuth2TokenEndpoint
import models.HubUser

/**
 * Authentication stuff.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class TokenExpiration extends Actor {

  protected def receive = {
    case EvictOAuth2Tokens => HubUser.all.foreach(u => u.evictExpiredAccessTokens())
  }
}

case class EvictOAuth2Tokens()
