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
import com.mongodb.gridfs.GridFSFile
import org.bson.types.ObjectId
import extensions.Extensions
import java.io.File
import play.api.libs.MimeTypes

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileUpload extends Controller with Extensions with Thumbnail {

  // ~~ public HTTP API

  /**
   * POST handler for uploading a file, given an UID that will be attached to it.
   * If the uploaded file is an image, thumbnails are created for it.
   * The response contains a JSON-Encoded array of objects representing the uploaded file.
   */
  def uploadFile(uid: String) = Action(parse.multipartFormData) {
    implicit request =>
      val uploaded = uploadFileInternal(uid, request.body.file("files[]").map {
        file => {
          Seq(Upload(file.ref.file, file.contentType.getOrElse(MimeTypes.forFileName(file.filename).getOrElse("unknown/unknown")), file.filename, file.ref.file.length()))
        }
      }.getOrElse(Seq()))
      
      if(uploaded.isEmpty) {
        // assume the worst
        InternalServerError("Error uploading file to the server")
      } else {
        Json(uploaded)
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
        deleteFileById(oid)
        Ok
      }
  }


  // ~~ public Scala API

  def getFilesForUID(uid: String): Seq[StoredFile] = fileStore.find(MongoDBObject(UPLOAD_UID_FIELD -> uid)) map {
    f => {
      val id = f.getId.asInstanceOf[ObjectId]
      val thumbnail = if (isImage(f)) {
        fileStore.findOne(MongoDBObject(FILE_POINTER_FIELD -> id)) match {
          case Some(t) => Some(t.id.asInstanceOf[ObjectId])
          case None => None
        }
      } else {
        None
      }
      StoredFile(id, f.getFilename, f.getContentType, f.getLength, thumbnail)
    }
  }

  /**
   * Attaches all files to an object, given the upload UID
   */
  def markFilesAttached(uid: String, objectIdentifier: String) {
    fileStore.find(MongoDBObject(UPLOAD_UID_FIELD -> uid)) map {
      f =>
      // yo listen up, this ain't implemented in the mongo driver and throws an UnsupportedOperationException
      // f.removeField("uid")
        f.put(UPLOAD_UID_FIELD, "")
        f.put(ITEM_POINTER_FIELD, objectIdentifier)
        f.save()
    }
  }

  /**
   * For all thumbnails and images of a particular file, sets their pointer to a given item, thus enabling direct lookup
   * using the item id.
   */
  def activateThumbnails(fileId: ObjectId, itemId: ObjectId) {
    val thumbnails = fileStore.find(MongoDBObject(FILE_POINTER_FIELD -> fileId))

    // deactive old thumbnails
    fileStore.find(MongoDBObject(THUMBNAIL_ITEM_POINTER_FIELD -> itemId)) foreach {
      theOldOne =>
        theOldOne.put(THUMBNAIL_ITEM_POINTER_FIELD, "")
        theOldOne.save()
    }

    // activate new thumbnails
    thumbnails foreach {
      thumb =>
        thumb.put(THUMBNAIL_ITEM_POINTER_FIELD, itemId)
        thumb.save()
    }

    // deactivate old image
    fileStore.findOne(MongoDBObject(IMAGE_ITEM_POINTER_FIELD -> itemId)) foreach {
      theOldOne =>
        theOldOne.put(IMAGE_ITEM_POINTER_FIELD, "")
        theOldOne.save
    }

    // activate new default image
    fileStore.findOne(fileId) foreach {
      theNewOne =>
        theNewOne.put(IMAGE_ITEM_POINTER_FIELD, itemId)
        theNewOne.save
    }
  }

  def isImage(f: GridFSFile) = f.getContentType.contains("image")


  // ~~~ PRIVATE


  private def uploadFileInternal(uid: String, uploads: Seq[Upload]): Seq[FileUploadResponse] = {
    val uploadedFiles = for (upload <- uploads) yield {
      val f = fileStore.createFile(upload.file)
      f.filename = upload.fileName
      f.contentType = upload.contentType
      f.put(UPLOAD_UID_FIELD, uid)
      f.save

      if (f._id == None) return Seq.empty

      // if this is an image, create a thumbnail for it so we can display it on the fly
      val thumbnailUrl: String = if (f.contentType.contains("image")) {
        fileStore.findOne(f._id.get) match {
          case Some(storedFile) =>
            val thumbnails = createThumbnails(storedFile, fileStore)
            if (thumbnails.size > 0) "/file/" + thumbnails.get(80).getOrElse(emptyThumbnailUrl) else emptyThumbnailUrl
          case None => ""
        }
      } else ""

      FileUploadResponse(upload.fileName, upload.length, "/file/" + f._id.get, thumbnailUrl, "/file/" + f._id.get)
    }
    uploadedFiles
  }

  /**
   * Deletes a file
   * @param id the mongo id of the
   */
  def deleteFileById(id: ObjectId) {
    fileStore.find(id) foreach {
      toDelete =>
      // remove thumbnails
        fileStore.find(MongoDBObject(FILE_POINTER_FIELD -> id)) foreach {
          t =>
            fileStore.remove(t.getId.asInstanceOf[ObjectId])
        }
        // remove the file itself
        fileStore.remove(id)
    }
  }

}

case class Upload(file: File, contentType: String, fileName: String, length: Long)