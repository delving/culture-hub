package actors

import akka.actor.Actor
import play.api.mvc.RequestHeader
import play.api.Logger
import collection.mutable.ArrayBuffer
import models.RouteAccess

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class RouteLogger extends Actor {

  val fileLog = Logger("routes")

  val mongoLogBuffer = new ArrayBuffer[RouteAccess]()

  def receive = {

    case RouteRequest(request) =>
      fileLog.info("%s %s".format(request.path, request.rawQueryString))
      mongoLogBuffer += RouteAccess(uri = request.path, queryString = request.queryString)

    case PersistRouteAccess =>
      mongoLogBuffer.foreach {
        access => RouteAccess.insert(access)
      }
      mongoLogBuffer.clear()


    case _ => // do nothing

  }

}

case class RouteRequest(request: RequestHeader)

case object PersistRouteAccess