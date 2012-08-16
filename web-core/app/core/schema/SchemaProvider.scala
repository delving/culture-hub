package core.schema

import akka.actor._
import akka.util.duration._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import eu.delving.schema._
import java.lang.Boolean
import play.api.libs.ws.{Response, WS}
import java.util.concurrent.TimeUnit
import play.api.{Play, Logger}
import java.io.File
import io.Source

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object SchemaProvider {

  lazy val default = {
    Akka.system.actorOf(Props[SchemaProvider])
  }

  case object Refresh


}

class SchemaProvider extends Actor {

  private var scheduler: Cancellable = null
  private var schemaRepository: SchemaRepository = null

  private lazy val fetcher = if(Play.isDev | Play.isTest) new LocalFetcher else new RemoteFetcher

  override def preStart() {
    scheduler = Akka.system.scheduler.schedule(0 seconds, 5 minutes, self, SchemaProvider.Refresh)
  }


  override def postStop() {
    scheduler.cancel()
  }

  protected def receive = {

    case SchemaProvider.Refresh =>
      schemaRepository = new SchemaRepository(fetcher)




  }

}

class RemoteFetcher extends Fetcher {

  val log = Logger("CultureHub")

  private val SCHEMA_REPO = "http://schemas.delving.eu"


  def isValidating: Boolean = true

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

  def isValidating: Boolean = false

  def fetchList(): String = repositoryFileContents("schema-repository.xml")

  def fetchFactDefinitions(definition: String): String = repositoryFileContents("fact-definition-list_1.0.0.xml")

  def fetchSchema(version: SchemaVersion, schemaType: SchemaType): String = repositoryFileContents(version.getPath(schemaType))

  private def repositoryFileContents(path: String): String = localRepository.map { repo =>
    val f = new File(repo, path)
    if (!f.exists()) {
      log.error("Could not find file " + f.getAbsolutePath)
      ""
    } else {
      Source.fromFile(f).getLines().mkString("\n")
    }
  }.getOrElse {
    log.error("Could not find local schema repository at ../" + DIR_NAME)
    ""
  }

}