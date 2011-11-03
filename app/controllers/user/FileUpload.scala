package controllers.user

import play.mvc.results.Result
import controllers.{Secure, DelvingController}
import play.Play
import models.DObject
import org.bson.types.ObjectId

/**
 * Router for the FileUpload service that either directly invokes the module API when running locally or invokes the remote
 * HTTP API when running remotely.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileUpload extends DelvingController with Secure {

  val mode = Play.configuration.getProperty("DoS.mode", "local")

  def uploadFile(uid: String): Result = {

    mode match {
      case "local" =>
        controllers.dos.FileUpload.uploadFile(uid)
      case "remote" =>
        // TODO
        Error("Not implemented!")
    }
  }

  def deleteFile(id: String): Result = {

    mode match {
      case "local" =>
        controllers.dos.FileUpload.deleteFile(id)
      case "remote" =>
        // TODO
        Error("Not implemented!")
    }

    // remove referring objects
    val oid = if (ObjectId.isValid(id)) new ObjectId(id) else (return Error(&("user.fileupload.removeError", id)))
    DObject.removeFile(oid)

    Ok
  }

}