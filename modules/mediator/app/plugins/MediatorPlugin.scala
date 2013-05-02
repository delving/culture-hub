package plugins

import _root_.util.{ Quotes, OrganizationConfigurationHandler, OrganizationConfigurationResourceHolder }
import play.api.{ Play, Logger, Configuration, Application }
import core.{ DomainServiceLocator, HubModule, AuthenticationService, CultureHubPlugin }
import models.{ HubUser, OrganizationConfiguration }
import java.io.File
import org.apache.ftpserver._
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.ftplet._
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication
import org.apache.ftpserver.command.{ NotSupportedCommand, CommandFactoryFactory }
import com.escalatesoft.subcut.inject.{ BindingModule, Injectable }
import scala.collection.JavaConverters._
import org.apache.ftpserver.usermanager.impl.{ ConcurrentLoginPermission, WritePermission, BaseUser }
import scala.collection.mutable
import play.api.libs.ws.WS
import play.api.mvc.Results
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import akka.actor.{ Props, ActorContext }
import actors.ImageProcessor
import controllers.ErrorReporter

/**
 * TODO TLS-SSL
 * TODO see if we can only have one FTP server and have directories per organization, with the appropriate permissions / view tweaks
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class MediatorPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = MediatorPlugin.PLUGIN_KEY

  var pluginConfigurations = Map.empty[OrganizationConfiguration, MediatorPluginConfiguration]

  override def onBuildConfiguration(configurations: Map[OrganizationConfiguration, Option[Configuration]]) {
    pluginConfigurations = configurations flatMap { config =>
      config._2 map { c =>
        val sourceDir = c.getString("sourceDirectory").getOrElse {
          throw missingConfigurationField("sourceDirectory", config._1.orgId)
        }
        val archiveDir = c.getString("archiveDirectory").getOrElse {
          throw missingConfigurationField("archiveDirectory", config._1.orgId)
        }
        val mediaServer = c.getString("mediaServerUrl").getOrElse {
          throw missingConfigurationField("mediaServerUrl", config._1.orgId)
        }
        val port = c.getInt("port").getOrElse {
          throw missingConfigurationField("port", config._1.orgId)
        }
        val sourceDirFile = new File(sourceDir)
        sourceDirFile.mkdirs()
        val archiveDirFile = new File(archiveDir)
        archiveDirFile.mkdirs()
        (config._1 -> MediatorPluginConfiguration(sourceDirFile, archiveDirFile, mediaServer, port, config._1))
      }
    }
  }

  override def onActorInitialization(context: ActorContext) {
    context.actorOf(Props[ImageProcessor], name = "imageProcessor")
  }

  import core.messages._
  /**
   * Handler for plugin messaging, based on Akka actors.
   * Override this method to handle particular messages.
   */
  override def receive = {
    case CollectionCreated(collectionId, configuration) =>
      val collectionSourceDir = new File(MediatorPlugin.pluginConfiguration(configuration).sourceDirectory, collectionId)
      if (!collectionSourceDir.exists()) {
        info(s"Created media source directory for collection ${configuration.orgId}:$collectionId")
        collectionSourceDir.mkdir()
      }

    case CollectionRenamed(oldCollectionId, newCollectionId, configuration) =>
      val oldCollectionSourceDir = new File(MediatorPlugin.pluginConfiguration(configuration).sourceDirectory, oldCollectionId)
      val newCollectionSourceDir = new File(MediatorPlugin.pluginConfiguration(configuration).sourceDirectory, newCollectionId)
      oldCollectionSourceDir.renameTo(newCollectionSourceDir)
      info(s"Renamed media source directory for collection ${configuration.orgId}:$oldCollectionId to  ${configuration.orgId}:$newCollectionId")
  }

  lazy val ftpServers = new OrganizationConfigurationResourceHolder[Option[MediatorPluginConfiguration], FtpServer]("ftpServers") {

    protected def resourceConfiguration(configuration: OrganizationConfiguration): Option[MediatorPluginConfiguration] = pluginConfigurations.get(configuration)

    protected def onAdd(resourceConfiguration: Option[MediatorPluginConfiguration]): Option[FtpServer] = resourceConfiguration map { config =>

      // because the hub is multi-tenant, but the FtpServer is not, we have to create one FTP server per organization
      // this is pretty stupid, but then again, we don't really have a choice. Life is tough.
      val serverFactory = new FtpServerFactory
      val listenerFactory = new ListenerFactory
      val commandFactoryFactory = new CommandFactoryFactory

      val dataConnectionConfigurationFactory = new DataConnectionConfigurationFactory()
      dataConnectionConfigurationFactory.setPassivePorts("20000-20100")
      val dataConnectionConfiguration = dataConnectionConfigurationFactory.createDataConnectionConfiguration()

      listenerFactory.setPort(config.port)
      listenerFactory.setDataConnectionConfiguration(dataConnectionConfiguration)
      serverFactory.addListener("default", listenerFactory.createListener())

      serverFactory.setUserManager(new HubUserManager(config.sourceDirectory.getAbsolutePath, HubModule)(config.configuration))

      val ftplets = new mutable.HashMap[String, Ftplet]()
      ftplets.put("mediator", new MediatorFtplet()(config.configuration))
      serverFactory.setFtplets(ftplets.asJava)

      // disallow creation of directories
      commandFactoryFactory.addCommand("MKD", new NotSupportedCommand)
      val commandFactory = commandFactoryFactory.createCommandFactory()
      serverFactory.setCommandFactory(commandFactory)

      // TODO SSL & TLS, see http://mina.apache.org/ftpserver-project/embedding_ftpserver.html
      val ftpServer = serverFactory.createServer()
      ftpServer.start()
      ftpServer
    }

    protected def onRemove(removed: FtpServer) {
      removed.stop()
    }
  }

  override def onStart() {
    OrganizationConfigurationHandler.registerResourceHolder(ftpServers)
  }

  override def onStop() {
    ftpServers.allResources.foreach { _.stop() }
  }
}

class MediatorFtplet(implicit configuration: OrganizationConfiguration) extends DefaultFtplet {

  val log = Logger("CultureHub")

  override def onUploadStart(session: FtpSession, request: FtpRequest): FtpletResult = {
    val path = request.getArgument
    if (path.split("/").length < 3) {
      log.info(s"[${session.getUser.getName}@${configuration.orgId}] Mediator: user tried to upload to wrong location '$path'")
      // be harsh. wuh-PSSSH!
      FtpletResult.DISCONNECT
    } else {
      super.onUploadStart(session, request)
    }
  }

  override def onUploadEnd(session: FtpSession, request: FtpRequest): FtpletResult = {
    log.info(s"[${session.getUser.getName}@${configuration.orgId}] Mediator: new file '${request.getArgument}' uploaded")

    handleNewFile(session.getUser.getName, request.getArgument)

    super.onUploadEnd(session, request)
  }

  private def handleNewFile(userName: String, path: String) {
    path.split("/") match {
      case p if p.length < 3 =>
        log.warn("File uploaded in the wrong place")
      case p =>
        val Array(set, fileName) = path.drop(1).split("/")

        // when we are presented a file with spaces in the name, remedy to it immediately
        // also lowercase it
        val name = {
          val renamed = fileName.replaceAll("\\s", "_").toLowerCase
          val srcDir = MediatorPlugin.pluginConfiguration.sourceDirectory
          log.debug(s"[$userName@${configuration.orgId}}] Mediator: renaming uploaded file to /$set/$renamed")
          val f = new File(srcDir, path)
          f.renameTo(new File(srcDir, s"/$set/$renamed"))
          renamed
        }

        def reportConnectionIssue(url: String, orgId: String, userName: String, statusCode: Option[Int]) {
          ErrorReporter.reportError(
            s"[Mediator Client] [$userName@orgId] Mediator Server unreachable",
            s"""
              |Master,
              |
              |user $userName tried to process a file, but the MediatorServer at '$url' is causing trouble.
              |
              |${if (statusCode.isDefined) "Status code is: " + statusCode else "Server is simply not reachable, see logs for more details"}
              |
              |Yours truly,
              |
              |The Mediator
              |----
              |${Quotes.randomQuote()}
            """.stripMargin
          )

        }

        val errorCallbackUrl = {
          val longestDomain = configuration.domains.sortBy(_.length).head
          val host = if (Play.isDev) s"http://$longestDomain.localhost:9000" else s"http://$longestDomain"
          host + "/media/fault/newFile"
        }
        val url = MediatorPlugin.pluginConfiguration.mediaServerUrl + "/media/command/newFile"

        try {
          WS
            .url(url)
            .withQueryString(
              "orgId" -> configuration.orgId,
              "set" -> set,
              "fileName" -> name,
              "userName" -> userName,
              "errorCallbackUrl" -> errorCallbackUrl
            )
            .post(Results.EmptyContent()).map { response =>
              if (response.ahcResponse.getStatusCode != 200) {
                log.error(s"[$userName@${configuration.orgId}}] Mediator: could not make request to media server at '$url', parameters: " +
                  s"orgId:${configuration.orgId}, set:$set, fileName:$name, errorCallbackUrl:$errorCallbackUrl")
                reportConnectionIssue(url, configuration.orgId, userName, Some(response.ahcResponse.getStatusCode))
              }
            }
        } catch {
          case t: Throwable =>
            log.error(s"[$userName@${configuration.orgId}}] Mediator: could not make request to media server", t)
            reportConnectionIssue(url, configuration.orgId, userName, None)

        }

    }

  }

}

class HubUserManager(baseDirectory: String, binding: BindingModule)(implicit configuration: OrganizationConfiguration) extends UserManager with Injectable {

  val log = Logger("CultureHub")

  implicit def bindingModule: BindingModule = binding

  implicit def hubUserToFtpUser(user: HubUser): User = {
    val u = new BaseUser {

      override def getName: String = user.userName

      override def getPassword: String = null

      override def getMaxIdleTime: Int = 0

      override def getEnabled: Boolean = true

      override def getHomeDirectory: String = baseDirectory
    }

    val authorities = Seq(
      new WritePermission,
      new ConcurrentLoginPermission(20, 2)
    //      new TransferRatePermission(4800, 4800)
    )

    u.setAuthorities(authorities.asJava)
    u
  }

  val authenticationService = inject[DomainServiceLocator[AuthenticationService]]

  def getUserByName(userName: String): User = HubUser.dao.findByUsername(userName).getOrElse(null)

  def getAllUserNames: Array[String] = Array()

  def delete(userName: String) {}

  def save(user: User) {}

  def doesExist(userName: String): Boolean = HubUser.dao.findByUsername(userName).isDefined

  def authenticate(authentication: Authentication): User = {
    if (authentication.isInstanceOf[UsernamePasswordAuthentication]) {
      val auth = authentication.asInstanceOf[UsernamePasswordAuthentication]
      if (authenticationService.byDomain.connect(auth.getUsername, auth.getPassword)) {
        HubUser.dao.findByUsername(auth.getUsername).getOrElse {
          log.warn("User could authenticate, but was not found")
          throw new ftplet.AuthenticationFailedException
        }
      } else {
        log.warn(s"[${auth.getUsername}@${configuration.orgId}] Wrong credentials")
        throw new ftplet.AuthenticationFailedException()
      }
    } else {
      log.warn("Authentication mechanism not yet supported")
      throw new ftplet.AuthenticationFailedException()
    }
  }

  def getAdminName: String = "The Flying Spaghetti Monster"

  def isAdmin(userName: String): Boolean = false
}

object MediatorPlugin {

  val PLUGIN_KEY = "mediator"

  def pluginConfiguration(implicit configuration: OrganizationConfiguration): MediatorPluginConfiguration = {
    CultureHubPlugin.getEnabledPlugins.find(_.pluginKey == MediatorPlugin.PLUGIN_KEY).map { p =>
      p.asInstanceOf[MediatorPlugin].pluginConfigurations(configuration)
    }.get // at this point we know that we have the plugin
  }

}

case class MediatorPluginConfiguration(
  sourceDirectory: File,
  archiveDirectory: File,
  mediaServerUrl: String,
  port: Int,
  configuration: OrganizationConfiguration)
