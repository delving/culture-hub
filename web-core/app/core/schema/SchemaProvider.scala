package core.schema

import akka.actor._
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import akka.pattern.ask
import scala.concurrent.Await
import core.schema.SchemaProvider._
import core.SchemaService
import play.api.Play.current
import play.api.libs.concurrent._
import eu.delving.schema._
import play.api.libs.ws.WS
import java.util.concurrent.TimeUnit
import play.api.{Play, Logger}
import scala.collection.JavaConverters._
import akka.util.Timeout
import models.OrganizationConfiguration
import play.api.libs.ws.Response
import util.FileSystemFetcher

/**
 * This component provides schemas and schema versions through a SchemaRepository that is updated every 5 minutes.
 *
 * It is wrapped inside of an actor to keep refresh operations safe
 *
 * TODO better error handling
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class SchemaProvider extends SchemaService {

  private val log = Logger("CultureHub")
  private def repository = Akka.system.actorFor("akka://application/user/schemaRepository")
  private implicit val timeout = Timeout(2000 milliseconds)

  override def refresh() {
    repository ! Refresh
  }

  override def getSchemas(implicit configuration: OrganizationConfiguration): Seq[eu.delving.schema.xml.Schema] = {
    getAllSchemas.filter(s => configuration.schemas.contains(s.prefix))
  }

  override def getAllSchemas: Seq[eu.delving.schema.xml.Schema] = {
    try {
      val future = (repository ? GetSchemas)
      Await.result(future, timeout.duration).asInstanceOf[Schemas].schemas.filterNot(s => s.versions.isEmpty)
    } catch {
      case t: Throwable =>
        log.error("Error while retrieving all schemas", t)
        Seq.empty
    }
  }

  override def getSchema(prefix: String, version: String, schemaType: SchemaType): Option[String] = {

    val future = repository ? GetSchema(new SchemaVersion(prefix, version), schemaType)

    try {
      Await.result(future, timeout.duration) match {
        case SchemaContent(schemaContent) =>
          log.trace(s"Retrieved schema $prefix $version $schemaType: $schemaContent")
          Option(schemaContent)
        case SchemaError(error: Throwable) =>
          log.error("Error while trying to retrieve schema %s of type %s: %s".format(
            version, schemaType.fileName, error.getMessage
          ), error)
          None
      }
    } catch {
      case t: Throwable =>
        log.error(s"Error while retrieving schema $prefix:$version from repository", t)
        None
    }
  }
}

object SchemaProvider {

  // ~~~ questions

  case object Refresh
  case object GetSchemas
  case class GetSchema(schemaVersion: SchemaVersion, schemaType: SchemaType)

  // ~~~ answers

  case class Schemas(schemas: Seq[eu.delving.schema.xml.Schema])
  case class SchemaContent(schemaContent: String)
  case class SchemaError(t: Throwable)

}

class SchemaRepositoryWrapper extends Actor {

  private val log = Logger("CultureHub")

  private var scheduler: Cancellable = null
  private var schemaRepository: SchemaRepository = null

  private lazy val fetcher = if(Play.isDev || Play.isTest) new FileSystemFetcher(false) else new RemoteFetcher

  override def preStart() {
    scheduler = Akka.system.scheduler.schedule(5 minutes, 5 minutes, self, SchemaProvider.Refresh)
  }


  override def postStop() {
    scheduler.cancel()
  }

  def receive = {

    case SchemaProvider.Refresh =>
      refresh()

    case GetSchemas =>
      if (schemaRepository == null) {
        log.warn("Schema repository was null?! Refreshing...")
        refresh()
      }
      sender ! Schemas(schemaRepository.getSchemas.asScala)

    case GetSchema(version, schemaType) =>
      if (schemaRepository == null) {
        log.warn("Schema repository was null?! Refreshing...")
        refresh()
      }

      try {
        val content = schemaRepository.getSchema(version, schemaType)
        sender ! SchemaContent(content)
      } catch {
        case t: Throwable =>
          sender ! SchemaError(t)
      }

  }

  private def refresh() {
    schemaRepository = new SchemaRepository(fetcher)
    log.info("Refreshed SchemaRepository, available schemas are: " + prefixes(schemaRepository.getSchemas.asScala))
  }

  private def prefixes(schemas: Seq[eu.delving.schema.xml.Schema]) = schemas.map(_.prefix).mkString(", ")

}

class RemoteFetcher extends Fetcher {

  val log = Logger("CultureHub")

  private val SCHEMA_REPO = "http://schemas.delving.eu"


  def isValidating: java.lang.Boolean = true

  def fetchList(): String = WS.url(SCHEMA_REPO + "/schema-repository.xml").get().await(5, TimeUnit.SECONDS).fold(
    { t: Throwable => log.error("RemoteFetcher: could not retrieve schema list", t); "" },
    { r: Response => r.getAHCResponse.getResponseBody("UTF-8") }
  )

  def fetchFactDefinitions(definition: String): String = {
    WS.url(SCHEMA_REPO + "/fact-definition-list_1.0.0.xml").get().await(5, TimeUnit.SECONDS).fold(
      { t: Throwable => log.error("RemoteFetcher: could not fetch fact definitions", t); "" },
      { r: Response => r.getAHCResponse.getResponseBody("UTF-8") }
    )
  }

  def fetchSchema(version: SchemaVersion, schemaType: SchemaType): String = WS.url(SCHEMA_REPO + version.getPath(schemaType)).get().await(5, TimeUnit.SECONDS).fold(
      { t: Throwable => log.error("RemoteFetcher: could not retrieve schema", t); "" },
      { r: Response => r.getAHCResponse.getResponseBody("UTF-8") }
    )

}