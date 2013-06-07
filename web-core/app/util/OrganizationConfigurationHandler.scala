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
import models.{ Config, HubMongoContext, OrganizationConfiguration }
import play.api.{ Logger, Play, Configuration }
import com.typesafe.config.ConfigFactory
import play.api.Play.current
import akka.actor.{ Cancellable, PoisonPill, Actor }
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import akka.pattern.ask
import scala.concurrent.Await
import akka.util.Timeout
import collection.mutable.ArrayBuffer
import play.api.libs.concurrent.Akka
import com.typesafe.config
import collection.JavaConverters._

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
  private val resourceHolders = new ArrayBuffer[(OrganizationConfigurationResourceHolder[_, _], Boolean)]()
  private var firstStageConfigurations: Seq[OrganizationConfiguration] = Seq.empty

  var firstStageDone: Boolean = false
  var secondStageDone: Boolean = false

  /**
   * Configures the system.
   * Configuration happens in two stages:
   * - first we parse & build the configuration for the various organizations
   * - then we configure all resource handlers
   *
   * Because some resource handlers only manifest themselves when they are used the first time
   * (e.g. because we didn't want to eagerly register all of them), we configure them on the fly based on the
   * configurations made available by the first stage. However, given that some resource handlers have dependencies on others,
   * we allow registration of prioritary resource handler to be initialized before the first stage is completed.
   *
   * @param isStartup whether this is startup time. Influences how errors are handled
   */
  def configure(isStartup: Boolean = false) {

    secondStageDone = false

    val secondStageCallback: () => Unit = { () =>
      secondStageDone = true
      log.info("Configuration successfully initialized")
      handler ! ResourceHolders(resourceHolders)
    }

    val eventuallyConfigured = handler ? Configure(resourceHolders, isStartup, secondStageCallback)

    Await.result(eventuallyConfigured, timeout.duration) match {

      case FirstStageSuccess(configurations) =>
        firstStageConfigurations = configurations
        firstStageDone = true

      case ConfigurationFailure(message) =>
        firstStageDone = true
        secondStageDone = true
        log.error(message)
        if (isStartup) {
          throw new RuntimeException(message)
        }
    }
  }

  def tearDown() {
    firstStageDone = false
    secondStageDone = false
    firstStageConfigurations = Seq.empty
    resourceHolders.clear()
    handler ! PoisonPill
  }

  def registerResourceHolder[A, B](holder: OrganizationConfigurationResourceHolder[A, B], initFirst: Boolean = false) {
    if (holder == null) {
      val st = new RuntimeException()
      st.fillInStackTrace()
      log.warn("Attempting to register null ResourceHolder", st)
    } else {
      if (!resourceHolders.exists(_._1 == holder)) {
        resourceHolders += ((holder, initFirst))

        // if we get registration requests after having started configuring, we need to do something with them
        if (firstStageDone) {
          holder.configure(firstStageConfigurations)
        }
      }
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
    if (!secondStageDone) {
      firstStageConfigurations
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

class OrganizationConfigurationHandler(plugins: Seq[CultureHubPlugin]) extends Actor {

  val log = Logger("CultureHub")

  private var scheduledTask: Cancellable = null

  private var organizationConfigurationsMap: Seq[(String, OrganizationConfiguration)] = Seq.empty
  private var domainLookupCache: HashMap[String, OrganizationConfiguration] = HashMap.empty

  private var resourceHoldersCache: Seq[(OrganizationConfigurationResourceHolder[_, _], Boolean)] = Seq.empty

  var organizationConfigurations: Seq[OrganizationConfiguration] = Seq.empty

  override def preStart() {
    scheduledTask = Akka.system.scheduler.schedule(5 minutes, 5 minutes, self, Refresh)
  }

  override def postStop() {
    scheduledTask.cancel()
  }

  def receive = {
    case Configure(holders, isStartup, secondPassCallback) =>

      val databaseConfigurations: Map[String, String] = if (!Play.isTest) {
        Play.application.configuration.getString(HubMongoContext.CONFIG_DB).map { configDb =>
          Config.findAll.map { config =>
            (config.orgId -> (s"""configurations.${config.orgId} { ${config.rawConfiguration} }"""))
          }.toMap
        }.getOrElse(Map.empty)
      } else {
        Map.empty
      }

      val parsed: Map[String, config.Config] = databaseConfigurations flatMap { c =>
        try {
          val parsed = ConfigFactory.parseString(c._2)
          Some((c._1 -> parsed))
        } catch {
          case t: Throwable =>
            log.error(s"Error while parsing configuration for organization ${c._1}", t)
            Config.addErrors(c._1, Seq(s"Error while parsing configuration: ${t.getMessage}"))
            None
        }
      }

      if (parsed.size != databaseConfigurations.size) {
        sender ! ConfigurationFailure("Parse error for some configurations, aborting refresh")
      } else {
        val instanceIdentifier = Play.current.configuration.getString("cultureHub.instanceIdentifier").getOrElse("default")
        val active = parsed filter { c =>
          val instancesKey = s"configurations.${c._1}.instances"
          c._2.hasPath(instancesKey) && c._2.getStringList(instancesKey).asScala.contains(instanceIdentifier)
        }

        val (configurations, errors: Seq[(String, String)]) = {
          val fromDatabase = if (!active.isEmpty) active.map(_._2).reduce(_.withFallback(_)) else ConfigFactory.empty()
          val merged = Play.application.configuration ++ Configuration(fromDatabase)
          OrganizationConfiguration.buildConfigurations(merged, plugins)
        }

        if (!errors.isEmpty) {

          // dynamic change caused trouble
          errors.groupBy(_._1) map { error =>
            Config.addErrors(error._1, error._2.map(_._2))
          }

          sender ! ConfigurationFailure("Invalid configuration(s). ¿Satan, is this you?\n\n" + errors.map(e => s"${e._1}: ${e._2}").mkString("\n"))

        } else if (errors.isEmpty && configurations.isEmpty) {
          // whaaaat?
          sender ! ConfigurationFailure("No configuration found! This is horrible! What should we do now?")
        } else {

          // only pick the active ones
          organizationConfigurations = configurations
          organizationConfigurationsMap = toDomainList(organizationConfigurations)
          domainLookupCache = HashMap.empty

          organizationConfigurations foreach { config =>
            Config.clearErrors(config.orgId)
          }

          // initialize important resource holders
          configureResourceHolders(holders.filter(_._2).map(_._1))

          // already release the new configurations now, in order to avoid a chicken-and-egg situation
          sender ! FirstStageSuccess(organizationConfigurations)

          configureResourceHolders(holders.map(_._1))
          secondPassCallback()
        }
      }

    case ResourceHolders(holders) =>
      resourceHoldersCache = holders

    case Refresh =>
      self ! Configure(resourceHoldersCache, false, { () => })

    case GetByOrgId(orgId) =>
      sender ! ConfigurationLookupResponse(organizationConfigurations.find(_.orgId == orgId))

    case GetByDomain(domain) =>
      lookupDomain(domain)
      sender ! ConfigurationLookupResponse(domainLookupCache.get(domain))

    case GetAll =>
      sender ! AllConfigurations(organizationConfigurations)
  }

  private def toDomainList(domainList: Seq[OrganizationConfiguration]) = domainList.flatMap(t => t.domains.map((_, t))).sortBy(_._1.length)

  private def lookupDomain(domain: String) = {
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
  }

  private def configureResourceHolders(holders: Seq[OrganizationConfigurationResourceHolder[_, _]]) {
    try {
      holders foreach { holder => holder.configure(organizationConfigurations) }
      log.trace(s"Initialized ${holders.size} resource holders")
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        sender ! ConfigurationFailure(t.getMessage)
    }
  }

}

// ~~~ questions

case class Configure(holders: Seq[(OrganizationConfigurationResourceHolder[_, _], Boolean)], isStartup: Boolean = false, secondStageCallback: () => Unit)
case class GetByOrgId(orgId: String)
case class GetByDomain(domain: String)
case class ResourceHolders(holders: Seq[(OrganizationConfigurationResourceHolder[_, _], Boolean)])
case object GetAll

case object Refresh

// ~~~ answers

case class ConfigurationLookupResponse(configuration: Option[OrganizationConfiguration])
case class FirstStageSuccess(configurations: Seq[OrganizationConfiguration])
case class ConfigurationFailure(message: String)
case class AllConfigurations(configurations: Seq[OrganizationConfiguration])