package actors

import akka.actor.{Cancellable, Actor}
import models.HubUser
import play.api.libs.concurrent.Akka
import akka.util.duration._
import play.api.Play.current

/**
 * Authentication stuff.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class TokenExpiration extends Actor {

  private var scheduler: Cancellable = null


  override def preStart() {
    scheduler = Akka.system.scheduler.schedule(
          0 seconds,
          5 minutes,
          self,
          EvictOAuth2Tokens
        )
  }


  override def postStop() {
    scheduler.cancel()
  }

  protected def receive = {
    case EvictOAuth2Tokens => HubUser.all.foreach(u => u.evictExpiredAccessTokens())
  }
}

case class EvictOAuth2Tokens()
