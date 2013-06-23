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
import models.OrganizationConfiguration
import controllers.OrganizationConfigurationAware
import core.storage.{ StoredFile, FileUploadResponse, FileStorage }

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileUpload extends Controller with Extensions with ThumbnailSupport with OrganizationConfigurationAware {

  // ~~ public HTTP API

  /**
   * POST handler for uploading a file, given an UID that will be attached to it.
   * If the uploaded file is an image, thumbnails are created for it.
   * The response contains a JSON-Encoded array of objects representing the uploaded file.
   */
  def uploadFile(uid: String) = MultitenantAction(parse.multipartFormData) {
    implicit request =>
      val uploaded = uploadFileInternal(uid, request.body.file("files[]").map {
        file =>
          {
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

  /**
   * DELETE handler for removing a file given an ID
   */
  def deleteFile(id: String) = MultitenantAction {
    implicit request =>
      FileStorage.deleteFile(id)
      Ok
  }

  // ~~~ Scala API

  /**
   * Attaches all files to an object, given the upload UID
   */
  def markFilesAttached(uid: String, objectIdentifier: String)(implicit configuration: OrganizationConfiguration) {
    val files = FileStorage.listFiles(uid, Some(FILE_TYPE_UNATTACHED))
    FileStorage.renameBucket(uid, objectIdentifier)
    FileStorage.setFileType(None, files)
  }

  // ~~~ PRIVATE

  private def uploadFileInternal(uid: String, uploads: Seq[Upload])(implicit configuration: OrganizationConfiguration): Seq[FileUploadResponse] = {
    val uploadedFiles = uploads.flatMap { upload =>
      storeFile(upload.file, upload.fileName, upload.contentType, uid) map { pair =>
        FileUploadResponse(upload.fileName, upload.length, "/file/" + pair._1.id, pair._2, "/file/" + pair._1.id)
      }
    }
    uploadedFiles
  }

  /**
   * Stores a file
   */
  def storeFile(file: File, fileName: String, contentType: String, uid: String)(implicit configuration: OrganizationConfiguration): Option[(StoredFile, String)] = {
    FileStorage.storeFile(file, contentType, fileName, uid, Some(FILE_TYPE_UNATTACHED), advertise = false).map { f =>

      // if this is an image, create a thumbnail for it so we can display it on the fly in the upload widget
      val thumbnailUrl: String = if (f.contentType.contains("image") || f.contentType.contains("pdf")) {
        fileStore(configuration).findOne(f.id) match {
          case Some(storedFile) =>
            val thumbnails = createThumbnails(storedFile, fileStore(configuration))
            if (thumbnails.size > 0) "/thumbnail/" + f.id.toString + "/80" else emptyThumbnailUrl
          case None => emptyThumbnailUrl
        }
      } else emptyThumbnailUrl

      (f, thumbnailUrl)

    }
  }

}

case class Upload(file: File, contentType: String, fileName: String, length: Long)