package actors

import akka.actor.{ Cancellable, Actor }
import play.api.mvc.RequestHeader
import play.api.Logger
import collection.mutable.Map
import collection.mutable.HashMap
import collection.mutable.ArrayBuffer
import models.{ OrganizationConfiguration, RouteAccess }
import util.OrganizationConfigurationHandler
import scala.concurrent.duration._
import play.api.libs.concurrent.Akka
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class RouteLogger extends Actor {

  private var scheduler: Cancellable = null

  override def preStart() {
    scheduler = Akka.system.scheduler.schedule(
      0 seconds,
      3 minutes, // TODO we may have to see what is the optimal value for this
      self,
      PersistRouteAccess
    )
  }

  override def postStop() {
    scheduler.cancel()
  }

  val fileLog = Logger("routes")

  // TODO make multi-tenant, i.e. Map DomainConfig -> Arraybuffer
  val mongoLogBuffer: Map[OrganizationConfiguration, ArrayBuffer[RouteAccess]] = new HashMap[OrganizationConfiguration, ArrayBuffer[RouteAccess]]()

  def receive = {

    case RouteRequest(request) =>
      val configuration = OrganizationConfigurationHandler.getByDomain(request.domain)
      fileLog.info("%s %s".format(request.path, request.rawQueryString))
      val routeAccess = RouteAccess(uri = request.path, queryString = request.queryString.map(a => (a._1.replaceAll("\\.", "_dot_") -> a._2)))
      if (mongoLogBuffer.contains(configuration)) {
        mongoLogBuffer(configuration).append(routeAccess)
      } else {
        val arr = new ArrayBuffer[RouteAccess]()
        arr.append(routeAccess)
        mongoLogBuffer += (configuration -> arr)
      }

    case PersistRouteAccess =>
      mongoLogBuffer.foreach {
        access =>
          {
            implicit val configuration = access._1
            RouteAccess.dao.insert(access._2)
          }
      }
      mongoLogBuffer.clear()

    case _ => // do nothing

  }

}

case class RouteRequest(request: RequestHeader)

case object PersistRouteAccess