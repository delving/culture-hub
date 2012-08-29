package core.schema

import akka.actor._
import akka.util.duration._
import akka.pattern.ask
import core.schema.SchemaProvider._
import core.SchemaService
import play.api.Play.current
import play.api.libs.concurrent._
import eu.delving.schema._
import play.api.libs.ws.WS
import java.util.concurrent.TimeUnit
import play.api.{Play, Logger}
import java.io.File
import io.Source
import scala.collection.JavaConverters._
import akka.util.Timeout
import models.DomainConfiguration
import play.api.libs.ws.Response

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

  override def getSchemas(implicit configuration: DomainConfiguration): Seq[eu.delving.schema.xml.Schema] = {
    getAllSchemas.filter(s => configuration.schemas.contains(s.prefix))
  }

  override def getAllSchemas: Seq[eu.delving.schema.xml.Schema] = (repository ? GetSchemas).asPromise.map {
    case Schemas(schemas) => schemas.filterNot(s => s.versions.isEmpty)
  }.await.fold(
    { t => throw t },
    { r => r }
  )

  override def getSchema(prefix: String, version: String, schemaType: SchemaType): Option[String] = (repository ? GetSchema(new SchemaVersion(prefix, version), schemaType)).asPromise.map {
    case SchemaContent(schemaContent) =>
      log.trace("Retrieved schema %s %s %s: ".format(prefix, version, schemaType.toString) + schemaContent)
      Option(schemaContent)
  }.await.fold(
    { t => throw t },
    { r => r }
  )

}

object SchemaProvider {

  // ~~~ questions

  case object Refresh
  case object GetSchemas
  case class GetSchema(schemaVersion: SchemaVersion, schemaType: SchemaType)

  // ~~~ answers

  case class Schemas(schemas: Seq[eu.delving.schema.xml.Schema])
  case class SchemaContent(schemaContent: String)

}

class SchemaRepositoryWrapper extends Actor {

  private val log = Logger("CultureHub")

  private var scheduler: Cancellable = null
  private var schemaRepository: SchemaRepository = null

  private lazy val fetcher = if(Play.isDev || Play.isTest) new LocalFetcher else new RemoteFetcher

  override def preStart() {
    scheduler = Akka.system.scheduler.schedule(5 minutes, 5 minutes, self, SchemaProvider.Refresh)
  }


  override def postStop() {
    scheduler.cancel()
  }

  protected def receive = {

    case SchemaProvider.Refresh =>
      refresh()

    case GetSchemas =>
      if (schemaRepository == null) {
        refresh()
      }
      sender ! Schemas(schemaRepository.getSchemas.asScala)

    case GetSchema(version, schemaType) =>
      if (schemaRepository == null) {
        refresh()
      }
      sender ! SchemaContent(schemaRepository.getSchema(version, schemaType))

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
    { t: Throwable => log.error("Could not retrieve schema list", t); "" },
    { r: Response => r.getAHCResponse.getResponseBody("UTF-8") }
  )

  def fetchFactDefinitions(definition: String): String = {
    WS.url(SCHEMA_REPO + "/fact-definition-list_1.0.0.xml").get().await(5, TimeUnit.SECONDS).fold(
      { t: Throwable => log.error("Could not fact definitions", t); "" },
      { r: Response => r.getAHCResponse.getResponseBody("UTF-8") }
    )
  }

  def fetchSchema(version: SchemaVersion, schemaType: SchemaType): String = WS.url(SCHEMA_REPO + version.getPath(schemaType)).get().await(5, TimeUnit.SECONDS).fold(
      { t: Throwable => log.error("Could not retrieve schema", t); "" },
      { r: Response => r.getAHCResponse.getResponseBody("UTF-8") }
    )

}

class LocalFetcher extends Fetcher {

  val log = Logger("CultureHub")

  val DIR_NAME = "schemas.delving.eu"

  lazy val localRepository = Play.application.getExistingFile("../" + DIR_NAME)

  def isValidating: java.lang.Boolean = false

  def fetchList(): String = repositoryFileContents("schema-repository.xml")

  def fetchFactDefinitions(definition: String): String = repositoryFileContents("fact-definition-list_1.0.0.xml")

  def fetchSchema(version: SchemaVersion, schemaType: SchemaType): String = repositoryFileContents(version.getPath(schemaType))

  private def repositoryFileContents(path: String): String = localRepository.map { repo =>
    val f = new File(repo.getAbsoluteFile, path)
    if (!f.exists()) {
      throw new RuntimeException("LocalFetcher: Could not find file " + f.getAbsolutePath)
    } else {
      Source.fromFile(f).getLines().mkString("\n")
    }
  }.getOrElse {
    throw new RuntimeException("LocalFetcher: Could not find local schema repository at ../" + DIR_NAME)
  }

}

case class Schema(prefix: String, version: String)