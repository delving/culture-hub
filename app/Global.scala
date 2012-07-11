/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

import actors._
import akka.actor.SupervisorStrategy.Restart
import akka.routing.RoundRobinRouter
import controllers.ReceiveSource
import core.indexing.IndexingService
import core.{CultureHubPlugin, HubServices}
import java.io.File
import models.{DataSetState, DataSet}
import play.api.libs.concurrent._
import akka.util.duration._
import akka.actor._
import core.mapping.MappingService
import play.api._
import libs.Files
import mvc.{Handler, RequestHeader}
import play.api.Play.current
import util.ThemeHandler
import eu.delving.culturehub.BuildInfo

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    if(!Play.isTest) {
      println("""
                ____       __      _
               / __ \___  / /   __(_)___  ____ _
              / / / / _ \/ / | / / / __ \/ __ `/
             / /_/ /  __/ /| |/ / / / / / /_/ /
            /_____/\___/_/ |___/_/_/ /_/\__, /
                                       /____/
               ______      ____                  __  __      __
              / ____/_  __/ / /___  __________  / / / /_  __/ /_
             / /   / / / / / __/ / / / ___/ _ \/ /_/ / / / / __ \
            / /___/ /_/ / / /_/ /_/ / /  /  __/ __  / /_/ / /_/ /
            \____/\__,_/_/\__/\__,_/_/   \___/_/ /_/\__,_/_.___/


            Version %s

            Sip-Creator Version %s

      """.format(BuildInfo.version, BuildInfo.sipCreator))
    }

    // temporary deployment trick
    if(Play.isProd) {
      val port = if(System.getProperty("http.port") == null) "9000" else System.getProperty("http.port")
      val runningPid = new File(current.path, "RUNNING_PID")
      Files.moveFile(runningPid, new File(current.path, "../" + port + "/RUNNING_PID"))
    }


    // ~~~ load themes
    ThemeHandler.startup()

    // ~~~ bootstrap services
    HubServices.init()
    MappingService.init()

    // ~~~ sanity check
    Play.configuration.getString("cultureHub.orgId") match {
      case Some(orgId) => 
        if(!HubServices.organizationService.exists(orgId)) {
          Logger("CultureHub").error("Organization %s does not exist on the configured Organizations service!".format(orgId))
          System.exit(-1)
        }
      case None =>
        Logger("CultureHub").error("No cultureHub.organization configured!")
        System.exit(-1)
    }

    // ~~~ bootstrap jobs

    // DataSet source parsing
    Akka.system.actorOf(Props[ReceiveSource].withRouter(
      RoundRobinRouter(5, supervisorStrategy = OneForOneStrategy() {
        case _ => Restart
      })
    ), name = "dataSetParser")

    // DataSet indexing
    val indexer = Akka.system.actorOf(Props[Processor])
    Akka.system.scheduler.schedule(
      0 seconds,
      10 seconds,
      indexer,
      ProcessDataSets
    )

    // DataSet housekeeping
    val dataSetHousekeeper = Akka.system.actorOf(Props[DataSetEventHousekeeper])
    Akka.system.scheduler.schedule(20 seconds, 30 minutes, dataSetHousekeeper, CleanupTransientEvents)

    // token expiration
    val tokenExpiration = Akka.system.actorOf(Props[TokenExpiration])
    Akka.system.scheduler.schedule(
      0 seconds,
      5 minutes,
      tokenExpiration,
      EvictOAuth2Tokens
    )

    // DoS
    val dos = Akka.system.actorOf(Props[TaskQueueActor])
    Akka.system.scheduler.schedule(
      0 seconds,
      10 seconds,
      dos,
      Look
    )

    // SOLR
    val solrCache = Akka.system.actorOf(Props[SolrCache])
    Akka.system.scheduler.schedule(
      30 seconds,
      120 minutes,
      solrCache,
      CacheSolrFields
    )

    // virtual collection update
    val virtualCollectionCount = Akka.system.actorOf(Props[VirtualCollectionCount])
    Akka.system.scheduler.schedule(
      1 minute,
      1 hour,
      virtualCollectionCount,
      UpdateVirtualCollectionCount
    )
    Akka.system.scheduler.schedule(
      1 minute,
      2 hours,
      virtualCollectionCount,
      UpdateVirtualCollection
    )

    // routes access logger
    val routeLogger = Akka.system.actorOf(Props[RouteLogger], name = "routeLogger")
    Akka.system.scheduler.schedule(
      0 seconds,
      3 minutes, // TODO we may have to see what is the optimal value for this
      routeLogger,
      PersistRouteAccess
    )

    // LATER: statistics computation
//    val statsLogger = Akka.system.actorOf(Props[StatisticsComputer])
//    Akka.system.scheduler.schedule(
//      0 seconds,
//      1 minutes, // TODO increase later on!!!
//      statsLogger,
//      ComputeStatistics
//    )


    // ~~~ cleanup set states
    // TODO move to appropriate component initialization

    DataSet.findByState(DataSetState.PROCESSING, DataSetState.CANCELLED).foreach {
      set =>
        DataSet.updateState(set, DataSetState.CANCELLED)
        try {
          IndexingService.deleteBySpec(set.orgId, set.spec)
        } catch {
          case t => Logger("CultureHub").error("Couldn't delete SOLR index for cancelled set %s:%s at startup".format(set.orgId, set.spec), t)
        } finally {
          DataSet.updateState(set, DataSetState.UPLOADED)
        }
    }

    // ~~~ load test data

    if (Play.isDev || Play.isTest) {
      util.TestDataLoader.load()
    }

  }

  override def onStop(app: Application) {
    if(!Play.isTest) {
    // close all Mongo connections
    import models.mongoContext._
    close()
    }

    // TODO move to component initialization
    // cleanly cancel all active processing
    DataSet.findByState(DataSetState.PROCESSING).foreach {
      set =>
        DataSet.updateState(set, DataSetState.CANCELLED)
    }
    Thread.sleep(2000)

  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    if(Play.isProd) {
      import play.api.mvc.Results._
      InternalServerError(views.html.errors.error(Some(ex)))
    } else {
      super.onError(request, ex)
    }
  }

  override def onHandlerNotFound(request: RequestHeader) = {

    if(Play.isProd) {
      import play.api.mvc.Results._
      InternalServerError(views.html.errors.notFound(request, "", None))
    } else {
      super.onHandlerNotFound(request)
    }

  }

  lazy val hubPlugins: List[CultureHubPlugin] = Play.application.plugins.filter(_.isInstanceOf[CultureHubPlugin]).map(_.asInstanceOf[CultureHubPlugin]).toList
  lazy val routes = hubPlugins.flatMap(_.routes)

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    val routeLogger = Akka.system.actorFor("akka://application/user/routeLogger")
    val apiRouteMatcher = """^/organizations/([A-Za-z0-9-]+)/api/(.)*""".r
    val matcher = apiRouteMatcher.pattern.matcher(request.uri)

    if(matcher.matches()) {
      // log route access, for API calls
      routeLogger ! RouteRequest(request)

      if(request.queryString.contains("explain") && request.queryString("explain").head == "true") {
        // redirect to the standard explain response
        return Some(controllers.api.Api.explainPath(matcher.group(1), request.path))
      }
    }

    // poor man's modular routing, based on CultureHub plugins

    val matches = routes.flatMap(r => {
      val matcher = r._1.pattern.matcher(request.path)
      if(matcher.matches()) {
        val pathElems = for(i <- 1 until matcher.groupCount() + 1) yield matcher.group(i)
        Some((pathElems.toList, r._2))
      } else {
        None
      }
    })

    if(matches.headOption.isDefined) {
      val handlerCall = matches.head
      val handler = handlerCall._2(handlerCall._1)
      Some(handler)
    } else {
      super.onRouteRequest(request)
    }
  }
}