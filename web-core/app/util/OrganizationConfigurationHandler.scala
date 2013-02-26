/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package util

import core.CultureHubPlugin
import collection.immutable.HashMap
import models.{ ConfigDAO, HubMongoContext, OrganizationConfiguration }
import play.api.{ Logger, Play, Configuration }
import com.typesafe.config.ConfigFactory
import play.api.Play.current
import akka.actor.{ PoisonPill, Actor }
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import akka.pattern.ask
import scala.concurrent.Await
import play.api.libs.concurrent.Akka
import akka.util.Timeout
import collection.mutable.ArrayBuffer

/**
 * Takes care of loading organisation-specific configuration.
 *
 * Because configurations may change dynamically at runtime, any kind of configuration-dependent resources need to be
 * handled via implementations of the [[ OrganizationConfigurationResourceHolder ]]. Make sure you register those instances
 * via the OrganizationConfigurationHandler#registerResourceHolder method.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 3/9/11 3:25 PM
 */
object OrganizationConfigurationHandler {

  private val log = Logger("CultureHub")
  private def handler = Akka.system.actorFor("akka://application/user/organizationConfigurationHandler")
  private implicit val timeout = Timeout(5000 milliseconds)
  private val resourceHolders = new ArrayBuffer[(Int, OrganizationConfigurationResourceHolder[_, _])]()
  private var firstPassConfigurations: Seq[OrganizationConfiguration] = Seq.empty

  var initializing: Boolean = false
  var firstPassDone: Boolean = false
  var secondPassDone: Boolean = false

  def configure(plugins: Seq[CultureHubPlugin], isStartup: Boolean = false) {

    initializing = true
    val eventuallyConfigured = handler ? Configure(plugins, resourceHolders.sortBy(_._1).reverse.map(_._2), isStartup)
    Await.result(eventuallyConfigured, timeout.duration) match {

      case FirstPassSuccess(configurations) =>
        firstPassConfigurations = configurations
        firstPassDone = true
        initializing = false
        // TODO find a better solution than this...
        // we basically can't send a message here because others have been queued in the meanwhile
        // yet we should only return from this method call after the second pass is done
        // and at the same time we have to respond earlier with the first pass configurations in order to do the second pass
        Thread.sleep(2000)
        secondPassDone = true
        log.info("Configuration successfully initialized")

      case ConfigurationSuccess =>
        initializing = false
        log.info("Configuration successfully refreshed")

      case ConfigurationFailure(message) =>
        initializing = false
        throw new RuntimeException(message)
    }
  }

  def teardown() {
    initializing = false
    firstPassDone = false
    secondPassDone = false
    firstPassConfigurations = Seq.empty
    resourceHolders.clear()
    handler ! PoisonPill
  }

  def registerResourceHolder[A, B](holder: OrganizationConfigurationResourceHolder[A, B], priority: Int = 0) {
    resourceHolders += ((priority, holder))

    // configure with the state we already know about
    if (initializing || firstPassDone) {
      holder.configure(getAllCurrentConfigurations)
    }
  }

  def getByOrgId(orgId: String) = {
    val future = handler ? GetByOrgId(orgId)
    Await.result(future, timeout.duration) match {
      case ConfigurationLookupResponse(maybeConfiguration) =>
        maybeConfiguration.getOrElse {
          throw new RuntimeException("No configuration for orgId " + orgId)
        }
    }
  }

  def hasConfiguration(domain: String) = {
    byDomain(domain) != None
  }

  def getByDomain(domain: String): OrganizationConfiguration = {
    byDomain(domain).getOrElse {
      throw new RuntimeException(s"No configuration for domain $domain")
    }
  }

  /**
   * Retrieves all currently available configurations.
   *
   * CAUTION: only use this when you really need to - and DO NOT use it if you want to build a cache of sorts on it, or need to be informed when a new organization appears.
   * For those cases, implement a [[ OrganizationConfigurationResourceHolder ]] instead.
   */
  def getAllCurrentConfigurations: Seq[OrganizationConfiguration] = {
    if (!secondPassDone) {
      firstPassConfigurations
    } else {
      val future = handler ? GetAll
      Await.result(future, timeout.duration) match {
        case AllConfigurations(configurations) =>
          configurations
        case _ => Seq.empty
      }
    }
  }

  private def byDomain(domain: String) = {
    val future = handler ? GetByDomain(domain)
    Await.result(future, timeout.duration) match {
      case ConfigurationLookupResponse(maybeConfiguration) =>
        maybeConfiguration
    }
  }

}

class OrganizationConfigurationHandler extends Actor {

  val log = Logger("CultureHub")

  private var organizationConfigurationsMap: Seq[(String, OrganizationConfiguration)] = Seq.empty
  private var domainLookupCache: HashMap[String, OrganizationConfiguration] = HashMap.empty
  private var invalidConfigurations = Seq.empty[String]

  var organizationConfigurations: Seq[OrganizationConfiguration] = Seq.empty

  def receive = {
    case Configure(plugins, holders, isStartup) =>

      val databaseConfiguration = Play.application.configuration.getString(HubMongoContext.CONFIG_DB).map { configDb =>
        ConfigDAO.findAll.map { config => s"""configurations.${config.orgId} { ${config.rawConfiguration} }""" }.mkString("\n")
      }.getOrElse("")

      println(databaseConfiguration)

      val config = Play.application.configuration ++ Configuration(ConfigFactory.parseString(databaseConfiguration))

      val (configurations, errors) = OrganizationConfiguration.buildConfigurations(config, plugins)
      organizationConfigurations = configurations
      invalidConfigurations = errors.map(_._1).toSeq

      if (!errors.isEmpty && isStartup) {
        sender ! ConfigurationFailure("Invalid configuration(s). ¿Satan, is this you?\n\n" + errors.map(e => s"${e._1}: ${e._2}").mkString("\n"))
      }

      organizationConfigurationsMap = toDomainList(organizationConfigurations)
      domainLookupCache = HashMap.empty

      if (isStartup) {
        // already release the new configurations now, in order to avoid a chicken-and-egg situation
        sender ! FirstPassSuccess(organizationConfigurations)
      }

      configureResourceHolders(holders)
      sender ! ConfigurationSuccess

    case GetByOrgId(orgId) =>
      sender ! ConfigurationLookupResponse(organizationConfigurations.find(_.orgId == orgId))

    case GetByDomain(domain) =>
      // note - this mechanism is vulnerable if you expose your server directly to the internet and pass on any kind of domains
      // so you shouldn't do this, and have a DNS filter of sorts in front of it, not plug the server directly to a wildcard DNS
      if (!domainLookupCache.contains(domain)) {
        // fetch by longest matching domain
        val configuration = organizationConfigurationsMap.foldLeft(("#", organizationConfigurations.head)) {
          (r: (String, OrganizationConfiguration), c: (String, OrganizationConfiguration)) =>
            {
              val rMatches = domain.startsWith(r._1)
              val cMatches = domain.startsWith(c._1)
              val rLonger = r._1.length() > c._1.length()

              if (rMatches && cMatches && rLonger) r
              else if (rMatches && cMatches && !rLonger) c
              else if (rMatches && !cMatches) r
              else if (cMatches && !rMatches) c
              else r // default
            }
        }._2
        domainLookupCache = domainLookupCache + (domain -> configuration)
      }
      sender ! ConfigurationLookupResponse(domainLookupCache.get(domain))

    case GetAll =>
      sender ! AllConfigurations(organizationConfigurations)
  }

  private def toDomainList(domainList: Seq[OrganizationConfiguration]) = domainList.flatMap(t => t.domains.map((_, t))).sortBy(_._1.length)

  private def configureResourceHolders(holders: Seq[OrganizationConfigurationResourceHolder[_, _]]) {
    try {
      log.debug(s"Initializing ${holders.size} resource holders")
      holders foreach { holder =>
        {
          val s = System.currentTimeMillis()
          holder.configure(organizationConfigurations)
        }
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        sender ! ConfigurationFailure(t.getMessage)
    }

  }

}

// ~~~ questions

case class Configure(plugins: Seq[CultureHubPlugin], holders: Seq[OrganizationConfigurationResourceHolder[_, _]], isStartup: Boolean = false)
case class GetByOrgId(orgId: String)
case class GetByDomain(domain: String)
case object GetAll

// ~~~ answers

case class ConfigurationLookupResponse(configuration: Option[OrganizationConfiguration])
case class FirstPassSuccess(configurations: Seq[OrganizationConfiguration])
case object ConfigurationSuccess
case class ConfigurationFailure(message: String)
case class AllConfigurations(configurations: Seq[OrganizationConfiguration])
