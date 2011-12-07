/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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