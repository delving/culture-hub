/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

import actors._
import com.mongodb.casbah.MongoConnection
import core.HubServices
import play.api.libs.concurrent._
import akka.util.duration._
import akka.actor._
import core.mapping.MappingService
import play.api._
import mvc.RequestHeader
import play.api.Play.current
import util.ThemeHandler

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
      """)
    }


    // ~~~ load themes
    ThemeHandler.startup()

    // ~~~ bootstrap services
    HubServices.init()
    MappingService.init()


    // ~~~ bootstrap jobs

    // DataSet indexing
    val indexer = Akka.system.actorOf(Props[Indexer])
    Akka.system.scheduler.schedule(
      0 seconds,
      10 seconds,
      indexer,
      IndexDataSets
    )

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
      10 seconds,
      120 seconds,
      solrCache,
      CacheSolrFields
    )


    // ~~~ load test data

    if (Play.isDev || Play.isTest) {
      util.TestDataLoader.load()
    }

  }

  override def onStop(app: Application) {
    // close all Mongo connections
    import models.mongoContext._
    close()
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
}