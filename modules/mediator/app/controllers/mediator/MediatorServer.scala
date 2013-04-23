package controllers.mediator

import play.api.mvc._
import controllers.DelvingController

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object MediatorServer extends DelvingController {

  def newFile(orgId: String, set: String, fileName: String, callbackUrl: String) = Action { implicit request =>
    log.info(s"[MediatorServer] Received notification for new file [$orgId] $set/$fileName")
    Ok
  }

}
