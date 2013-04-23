package controllers.mediator.organization

import controllers.OrganizationController
import play.api.mvc._
import plugins.MediatorPlugin
import java.io.File

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object Mediator extends OrganizationController {

  def collection(collection: String) = OrganizationAdmin {

    Action { implicit request =>

      val collectionSourceDir = new File(MediatorPlugin.pluginConfiguration.sourceDirectory, collection)
      if (!collectionSourceDir.exists()) collectionSourceDir.mkdir()

      val files = collectionSourceDir.listFiles

      Ok
    }
  }

  def fileHandled(orgId: String, set: String, fileName: String, error: Option[String]) = Action { implicit request =>
    log.debug(s"[$orgId] Received file handling response from media server for $set/$fileName, ${if (error.isDefined) "with error: " + error.get}")

    // TODO notify user

    Ok
  }

}
