package core.schema

import akka.actor._
import akka.util.duration._
import akka.pattern.ask
import core.schema.SchemaProvider.{SchemaContent, GetSchema, Schemas, GetSchemas}
import play.api.Play.current
import play.api.libs.concurrent._
import eu.delving.schema._
import play.api.libs.ws.{Response, WS}
import java.util.concurrent.TimeUnit
import play.api.{Play, Logger}
import java.io.File
import io.Source
import scala.collection.JavaConverters._
import akka.util.Timeout
import models.DomainConfiguration

/**
 * This component provides schemas and schema versions through a SchemaRepository that is updated every 5 minutes.
 *
 * It is wrapped inside of an actor to keep refresh operations safe
 *
 * TODO better error handling
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object SchemaProvider {

  private val log = Logger("CultureHub")
  private lazy val repository = Akka.system.actorOf(Props[SchemaRepositoryWrapper])
  private implicit val timeout = Timeout(1000 milliseconds)

  /**
   * Refreshes the repository, i.e. fetches the latest version of schemas
   */
  def refresh() {
    repository ! Refresh
  }

  /**
   * Retrieves all Schemas that are active for the current configuration
   * @param configuration the active DomainConfiguration
   * @return a sequence of Schema
   */
  def getSchemas(implicit configuration: DomainConfiguration): Seq[eu.delving.schema.xml.Schema] = {
    getAllSchemas.filter(s => configuration.schemas.contains(s.prefix))
  }

  /**
   * Retrieves all Schemas the repository knows about
   * @return all schemas
   */
  def getAllSchemas: Seq[eu.delving.schema.xml.Schema] = (repository ? GetSchemas).asPromise.map {
      case Schemas(schemas) => schemas.filterNot(s => s.versions.isEmpty)
    }.await.fold(
      { t => throw t },
      { r => r }
    )

  /**
   * Tries to retrieve the Schema content
   * @param prefix the schema prefidx
   * @param version the schema version
   * @param schemaType the schema type
   * @return an optional string with the schema contents
   */
  def getSchema(prefix: String, version: String, schemaType: SchemaType): Option[String] = (repository ? GetSchema(new SchemaVersion(prefix, version), schemaType)).asPromise.map {
    case SchemaContent(schemaContent) =>
      log.trace("Retrieved schema %s %s %s: ".format(prefix, version, schemaType.toString) + schemaContent)
      Option(schemaContent)
  }.await.fold(
    { t => throw t },
    { r => r }
  )

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
      schemaRepository = new SchemaRepository(fetcher)
      log.info("Refreshed SchemaRepository, available schemas are: " + prefixes(schemaRepository.getSchemas.asScala))

    case GetSchemas =>
      sender ! Schemas(schemaRepository.getSchemas.asScala)

    case GetSchema(version, schemaType) =>
      sender ! SchemaContent(schemaRepository.getSchema(version, schemaType))

  }

  def prefixes(schemas: Seq[eu.delving.schema.xml.Schema]) = schemas.map(_.prefix).mkString(", ")

}

class RemoteFetcher extends Fetcher {

  val log = Logger("CultureHub")

  private val SCHEMA_REPO = "http://schemas.delving.eu"


  def isValidating: java.lang.Boolean = true

  def fetchList(): String = WS.url(SCHEMA_REPO + "/schema-repository.xml").get().await(5, TimeUnit.SECONDS).fold(
    { t: Throwable => log.error("Could not retrieve schema list", t); "" },
    { r: Response => r.body }
  )

  def fetchFactDefinitions(definition: String): String = {
    WS.url(SCHEMA_REPO + "/fact-definition-list_1.0.0.xml").get().await(5, TimeUnit.SECONDS).fold(
      { t: Throwable => log.error("Could not fact definitions", t); "" },
      { r: Response => r.body }
    )
  }

  def fetchSchema(version: SchemaVersion, schemaType: SchemaType): String = WS.url(version.getPath(schemaType)).get().await(5, TimeUnit.SECONDS).fold(
      { t: Throwable => log.error("Could not retrieve schema", t); "" },
      { r: Response => r.body }
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