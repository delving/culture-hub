/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

import actors._
import core.CultureHubPlugin
import play.api.libs.concurrent._
import akka.actor._
import play.api._
import mvc.{Handler, RequestHeader}
import play.api.Play.current
import util.DomainConfigurationHandler
import eu.delving.culturehub.BuildInfo
import play.api.mvc.Results._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    if (!Play.isTest) {
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

    // ~~~ bootstrap jobs

    // token expiration
    Akka.system.actorOf(Props[TokenExpiration])

    // DoS
    Akka.system.actorOf(Props[TaskQueueActor])

    // SOLR
    Akka.system.actorOf(Props[SolrCache])

    // routes access logger
    Akka.system.actorOf(Props[RouteLogger], name = "routeLogger")

    // ~~~ load test data

    if (Play.isDev || Play.isTest) {
      util.TestDataLoader.load()
    }

  }

  override def onStop(app: Application) {
    if (!Play.isTest) {
    // close all Mongo connections
    import models.HubMongoContext._
    close()
    }
  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    if (Play.isProd) {
      import play.api.mvc.Results._
      InternalServerError(views.html.errors.error(Some(ex), None))
    } else {
      super.onError(request, ex)
    }
  }

  override def onHandlerNotFound(request: RequestHeader) = {

    if(Play.isProd) {
      InternalServerError(views.html.errors.notFound(request, "", None))
    } else {
      super.onHandlerNotFound(request)
    }

  }

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {

    // check if we access a configured domain
    if (!DomainConfigurationHandler.hasConfiguration(request.domain)) {
      Logger("CultureHub").debug("Accessed invalid domain %s, redirecting...".format(request.domain))
      Some(controllers.Default.redirect(Play.configuration.getString("defaultDomainRedirect").getOrElse("http://www.delving.eu")))
    } else {
      implicit val configuration = DomainConfigurationHandler.getByDomain(request.domain)
      val routes = CultureHubPlugin.getEnabledPlugins.flatMap(_.routes)

      val routeLogger = Akka.system.actorFor("akka://application/user/routeLogger")
      val apiRouteMatcher = """^/organizations/([A-Za-z0-9-]+)/api/(.)*""".r
      val matcher = apiRouteMatcher.pattern.matcher(request.uri)

      if (matcher.matches()) {
        // log route access, for API calls
        routeLogger ! RouteRequest(request)

        // TODO proper routing for search
        if(request.queryString.contains("explain") && request.queryString("explain").head == "true" && !request.path.contains("search")) {
          // redirect to the standard explain response
          return Some(controllers.api.Api.explainPath(matcher.group(1), request.path))
        }
      }

      // poor man's modular routing, based on CultureHub plugins
      // thou shalt not try to improve this code, it's meant to be provided by the framework at some point

      val matches = routes.flatMap(r => {
        val matcher = r._1._2.pattern.matcher(request.path)
        if (request.method == r._1._1 && matcher.matches()) {
          val pathElems = for(i <- 1 until matcher.groupCount() + 1) yield matcher.group(i)
          Some((pathElems.toList, r._2))
        } else {
          None
        }
      })

      if (matches.headOption.isDefined) {
        val handlerCall = matches.head
        val handler = handlerCall._2(handlerCall._1, request.queryString.filterNot(_._2.isEmpty).map(qs => (qs._1 -> qs._2.head)))
        Some(handler)
      } else {
        super.onRouteRequest(request)
      }
    }
  }
}