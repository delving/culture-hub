package plugins

import _root_.util.{ OrganizationConfigurationHandler, OrganizationConfigurationResourceHolder }
import play.api.{ Play, Logger, Configuration, Application }
import core.{ DomainServiceLocator, HubModule, AuthenticationService, CultureHubPlugin }
import models.{ HubUser, OrganizationConfiguration }
import java.io.File
import org.apache.ftpserver._
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.ftplet._
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication
import com.escalatesoft.subcut.inject.{ BindingModule, Injectable }
import scala.collection.JavaConverters._
import org.apache.ftpserver.usermanager.impl.{ ConcurrentLoginPermission, WritePermission, BaseUser }
import scala.collection.mutable
import play.api.libs.ws.WS
import play.api.mvc.Results
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

/**
 * TODO fix permissions to only allow upload in set folders, and to disallow mkdir
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
        val mediaServer = c.getString("mediaServerUrl").getOrElse {
          throw missingConfigurationField("mediaServerUrl", config._1.orgId)
        }
        val port = c.getInt("port").getOrElse {
          throw missingConfigurationField("port", config._1.orgId)
        }
        val f = new File(sourceDir)
        f.mkdirs()
        (config._1 -> MediatorPluginConfiguration(f, mediaServer, port, config._1))
      }
    }
  }

  lazy val ftpServers = new OrganizationConfigurationResourceHolder[Option[MediatorPluginConfiguration], FtpServer]("ftpServers") {

    protected def resourceConfiguration(configuration: OrganizationConfiguration): Option[MediatorPluginConfiguration] = pluginConfigurations.get(configuration)

    protected def onAdd(resourceConfiguration: Option[MediatorPluginConfiguration]): Option[FtpServer] = resourceConfiguration map { config =>

      // because the hub is multi-tenant, but the FtpServer is not, we have to create one FTP server per organization
      // this is pretty stupid, but then again, we don't really have a choice. Life is tough.
      val serverFactory = new FtpServerFactory
      val listenerFactory = new ListenerFactory()

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

  val log = Logger("Culture-Hub")

  override def onUploadEnd(session: FtpSession, request: FtpRequest): FtpletResult = {
    // FIXME - logs don't seem to work here ?!
    // log.info(s"[${session.getUser.getName}@${configuration.orgId}] Mediator: new file '${request.getArgument}' uploaded")
    println(s"[${session.getUser.getName}@${configuration.orgId}] Mediator: new file '${request.getArgument}' uploaded")

    handleNewFile(session.getUser.getName, request.getArgument)

    super.onUploadEnd(session, request)
  }

  private def handleNewFile(userName: String, path: String) {
    path.split("/") match {
      case p if p.length < 3 =>
        println("File uploaded in the wrong place")
      case p if p.length > 3 =>
        println("Can't have subfolders")
      case p =>
        val Array(set, fileName) = path.drop(1).split("/")
        println(s"We shall process file $set $fileName")

        // when we are presented a file with spaces in the name, remedy to it immediately
        val name = if (fileName.indexOf(" ") > -1) {
          val renamed = fileName.replaceAll("\\s", "_")
          val srcDir = MediatorPlugin.pluginConfiguration.sourceDirectory
          println(s"[$userName@${configuration.orgId}}] Renaming uploaded file to /$set/$renamed")
          val f = new File(srcDir, path)
          f.renameTo(new File(srcDir, s"/$set/$renamed"))
          renamed
        } else {
          fileName
        }

        try {
          val callbackUrl = {
            val host = if (Play.isDev) s"http://${configuration.domains.head}:9000" else s"http://${configuration.domains.head}"
            host + "/media/event/fileHandled" // TODO think of a better client-side URL scheme
          }
          WS
            .url(MediatorPlugin.pluginConfiguration.mediaServerUrl + "/media/event/newFile")
            .withQueryString(
              "orgId" -> configuration.orgId,
              "set" -> set,
              "fileName" -> name,
              "callbackUrl" -> callbackUrl
            )
            .post(Results.EmptyContent()).map { response =>
              println(response.ahcResponse.getStatusCode)
            }
        } catch {
          case t: Throwable =>
            t.printStackTrace()
        }

    }

  }

}

class HubUserManager(baseDirectory: String, binding: BindingModule)(implicit configuration: OrganizationConfiguration) extends UserManager with Injectable {

  val log = Logger("Culture-Hub")

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

case class MediatorPluginConfiguration(sourceDirectory: File, mediaServerUrl: String, port: Int, configuration: OrganizationConfiguration)
