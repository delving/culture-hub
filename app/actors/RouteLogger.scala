package actors

import akka.actor.Actor
import play.api.mvc.RequestHeader
import play.api.Logger

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class RouteLogger extends Actor {

  val fileLog = Logger("routes")

  def receive = {

    case RouteRequest(request) =>
      fileLog.info("%s %s".format(request.uri, request.rawQueryString))

    case _ => // do nothing

  }

}

case class RouteRequest(request: RequestHeader)