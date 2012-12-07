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

package controllers.dos

import play.api.mvc._
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import extensions.Extensions
import java.io.File
import play.api.libs.MimeTypes
import models.DomainConfiguration
import controllers.DomainConfigurationAware
import core.storage.{FileUploadResponse, FileStorage}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileUpload extends Controller with Extensions with Thumbnail with DomainConfigurationAware {

  // ~~ public HTTP API

  /**
   * POST handler for uploading a file, given an UID that will be attached to it.
   * If the uploaded file is an image, thumbnails are created for it.
   * The response contains a JSON-Encoded array of objects representing the uploaded file.
   */
  def uploadFile(uid: String) = DomainConfigured {
    Action(parse.multipartFormData) {
      implicit request =>
        val uploaded = uploadFileInternal(uid, request.body.file("files[]").map {
          file => {
            Seq(Upload(file.ref.file, file.contentType.getOrElse(MimeTypes.forFileName(file.filename).getOrElse("unknown/unknown")), file.filename, file.ref.file.length()))
          }
        }.getOrElse(Seq()))

        if (uploaded.isEmpty) {
          // assume the worst
          InternalServerError("Error uploading file to the server")
        } else {
          Json(uploaded)
        }
    }

  }

  /**
   * DELETE handler for removing a file given an ID
   */
  def deleteFile(id: String) = Action {
    implicit request =>
      if (!ObjectId.isValid(id)) {
        InternalServerError("Invalid file ID " + id)
      } else {
        val oid = new ObjectId(id)
        FileStorage.deleteFileById(oid)
        Ok
      }
  }

  // ~~~ PRIVATE


  private def uploadFileInternal(uid: String, uploads: Seq[Upload])(implicit configuration: DomainConfiguration): Seq[FileUploadResponse] = {
    val uploadedFiles = for (upload <- uploads) yield {
      val (f, thumbnailUrl) = storeFile(upload.file, upload.fileName, upload.contentType, uid)
      FileUploadResponse(upload.fileName, upload.length, "/file/" + f._id.get, thumbnailUrl, "/file/" + f._id.get)
    }
    uploadedFiles
  }

  /**
   * Stores a file
   */
  def storeFile(file: File, fileName: String, contentType: String, uid: String)(implicit configuration: DomainConfiguration) = {
    val f = fileStore(configuration).createFile(file)
    f.filename = fileName
    f.contentType = contentType
    f.put(UPLOAD_UID_FIELD, uid)
    f.save

    // if this is an image, create a thumbnail for it so we can display it on the fly in the upload widget
    val thumbnailUrl: String = if (f.contentType.map(c => c.contains("image") || c.contains("pdf")).getOrElse(false)) {
      fileStore(configuration).findOne(f._id.get) match {
        case Some(storedFile) =>
          val thumbnails = createThumbnails(storedFile, fileStore(configuration))
          if (thumbnails.size > 0) "/thumbnail/" + f.id.toString + "/80" else emptyThumbnailUrl
        case None => emptyThumbnailUrl
      }
    } else emptyThumbnailUrl

    (f, thumbnailUrl)
  }

}

case class Upload(file: File, contentType: String, fileName: String, length: Long)