package controllers.dos

import play.api.mvc._

import org.bson.types.ObjectId
import play.api.libs.iteratee.Enumerator
import controllers.OrganizationConfigurationAware

/**
 * Common controller for handling files, no matter from where.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileStore extends Controller with OrganizationConfigurationAware {

  // ~~~ public HTTP API

  def get(id: String): Action[AnyContent] = OrganizationConfigured {
    Action {
      implicit request =>
        if (!ObjectId.isValid(id)) {
          BadRequest("Invalid ID " + id)
        } else {
          val oid = new ObjectId(id)
          fileStore(configuration).findOne(oid) match {
            case Some(file) =>
              Ok.stream(Enumerator.fromStream(file.inputStream)).withHeaders(
                (CONTENT_DISPOSITION -> ("attachment; filename=" + file.filename.getOrElse(id))),
                (CONTENT_LENGTH -> file.length.toString),
                (CONTENT_TYPE -> file.contentType.getOrElse("unknown/unknown")))
            case None =>
              NotFound("Could not find file with ID " + id)
          }
        }
    }
  }

}