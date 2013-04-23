package controllers.mediator

import play.api.mvc._
import controllers.DelvingController
import plugins.MediatorPlugin
import java.io.File
import play.api.libs.MimeTypes
import akka.actor.ActorRef
import play.api.libs.concurrent.Akka
import actors.ProcessImage
import play.api.Play.current

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object MediatorServer extends DelvingController {

  def imageProcessor: ActorRef = Akka.system.actorFor("akka://application/user/plugin-mediator/imageProcessor")

  def newFile(orgId: String, set: String, fileName: String, callbackUrl: String) = OrganizationConfigured {
    Action { implicit request =>
      log.info(s"[MediatorServer] Received notification for new file [$orgId] $set/$fileName")

      val file = new File(MediatorPlugin.pluginConfiguration.sourceDirectory, s"/$set/$fileName")

      if (!file.exists()) {
        log.error(s"[MediatorServer] File ${file.getAbsolutePath} could not be found on disk")
        NotFound
      } else if (!isImage(file)) {
        log.error(s"[MediatorServer] File ${file.getAbsolutePath} is not an image")
        BadRequest
      } else {
        imageProcessor ! ProcessImage(orgId, set, file, callbackUrl, configuration)
        Ok
      }
    }
  }

  private def isImage(file: File) = MimeTypes.forFileName(file.getName).map(_.contains("image")).getOrElse(false)

}
