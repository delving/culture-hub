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
import org.bson.types.ObjectId
import controllers.DelvingController
import core.storage.FileStorage

/**
 * Router for the FileUpload service that either directly invokes the module API when running locally or invokes the remote
 * HTTP API when running remotely.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileUpload extends DelvingController {

  def uploadFile(uid: String) = ConnectedUserAction {
    controllers.dos.FileUpload.uploadFile(uid)
  }

  def deleteFile(id: String): Action[AnyContent] = ConnectedUserAction {
    Action {
      implicit request =>
        FileStorage.deleteFile(id)
        Ok
    }
  }

}