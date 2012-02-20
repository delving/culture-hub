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

import play.api.mvc._
import models.DObject
import org.bson.types.ObjectId
import controllers.DelvingController
import play.api.Play
import play.api.Play.current
import play.api.i18n.Messages

/**
 * Router for the FileUpload service that either directly invokes the module API when running locally or invokes the remote
 * HTTP API when running remotely.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileUpload extends DelvingController {

  val mode = Play.configuration.getString("DoS.mode").getOrElse("local")

  def uploadFile(uid: String) = ConnectedUserAction {
    controllers.dos.FileUpload.uploadFile(uid)
  }

  def deleteFile(id: String): Action[AnyContent] = ConnectedUserAction {
    Action {
      implicit request =>
        if(!ObjectId.isValid(id)) {
          BadRequest("Invalid id " + id)
        } else {
          mode match {
            case "local" => {
              controllers.dos.FileUpload.deleteFileById(new ObjectId(id))
              Ok
            }
            case "remote" => InternalServerError("Not implemented")
          }
        }

        // remove refering objects
        if (!ObjectId.isValid(id)) {
          Error(Messages("user.fileupload.removeError", id))
        } else {
          val oid = new ObjectId(id)
          DObject.removeFile(oid)
          Ok
        }

    }
  }

}