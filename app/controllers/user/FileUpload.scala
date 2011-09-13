package controllers.user

import com.mongodb.casbah.Imports._
import play.mvc.results.Result
import collection.JavaConversions._
import com.mongodb.casbah.gridfs.GridFSDBFile
import play.mvc.Util
import com.mongodb.gridfs.GridFSFile
import controllers.{FileStore, ImageCacheService, Secure, DelvingController}
import controllers.FileStore._
import org.bson.types.ObjectId
import models.{DObject, StoredFile}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileUpload extends DelvingController with Secure {

  val fs = controllers.FileStore.fs

  def uploadFile(uid: String): Result = {
    val uploads = request.args.get("__UPLOADS").asInstanceOf[java.util.List[play.data.Upload]]

    val uploadedFiles = for (upload: play.data.Upload <- asScalaIterable(uploads)) yield {
      val f = fs.createFile(upload.asStream())
      f.filename = upload.getFileName
      f.contentType = upload.getContentType
      f.put(controllers.FileStore.UPLOAD_UID_FIELD, uid)
      f.save

      if (f._id == None) return Error("Error saving uploaded file")

      // if this is an image, create a thumbnail for it so we can display it on the fly
      val thumbnailUrl: String = if (f.contentType.contains("image")) {
        fs.findOne(f._id.get) match {
          case Some(storedFile) => createThumbnail(storedFile, controllers.FileStore.THUMBNAIL_WIDTH) match {
            case Some(thumb) => "/file/" + thumb
            case None => controllers.FileStore.emptyThumbnail
          }
          case None => ""
        }
      } else ""

      FileUploadResponse(upload.getFileName, upload.getSize.longValue(), "/file/" + f._id.get, thumbnailUrl, "/file/" + f._id.get)
    }
    Json(uploadedFiles)
  }

  def deleteFile(id: String): Result = {
    val oid = id getOrElse(return Error("Invalid file ID " + id))
    fs.find(oid) foreach { toDelete =>
      fs.find(MongoDBObject(FILE_POINTER_FIELD -> oid)) foreach { t =>
        fs.remove(t.getId.asInstanceOf[ObjectId])
      }
      fs.remove(oid)
    }

    // remove referring objects
    DObject.removeFile(oid)

    Ok
  }

  /**creates a thumbnail and stores a pointer to the original image **/
  @Util def createThumbnail(image: GridFSDBFile, width: Int) = {
    val thumbnailStream = ImageCacheService.createThumbnail(image.inputStream, width)
    val thumbnail = fs.createFile(thumbnailStream)
    thumbnail.filename = image.filename
    thumbnail.contentType = "image/jpeg"
    thumbnail.put(controllers.FileStore.FILE_POINTER_FIELD, image._id)
    thumbnail.save
    thumbnail._id
  }
  @Util def fetchFilesForUID(uid: String): Seq[StoredFile] = fs.find(MongoDBObject("uid" -> uid)) map {
    f => {
      val id = f.getId.asInstanceOf[ObjectId]
      val thumbnail = if (isImage(f)) {
        fs.findOne(MongoDBObject(controllers.FileStore.FILE_POINTER_FIELD -> id)) match {
          case Some(t) => Some(t.id.asInstanceOf[ObjectId])
          case None => None
        }
      } else {
        None
      }
      StoredFile(id, f.getFilename, f.getContentType, f.getLength, thumbnail)
    }
  }

  /**Attaches all files to an object, given the upload UID **/
  @Util def markFilesAttached(uid: String, objectId: ObjectId) {
    fs.find(MongoDBObject("uid" -> uid)) map {
      f =>
      // yo listen up, this ain't implemented in the mongo driver and throws an UnsupportedOperationException
      // f.removeField("uid")
        f.put(controllers.FileStore.UPLOAD_UID_FIELD, "")
        f.save()
    }
  }

  @Util def isImage(f: GridFSFile) = f.getContentType.contains("image")

  /**given a file ID, set its thumbnail as The Chosen One for an object **/
  @Util def activateThumbnail(fileId: ObjectId, objectId: ObjectId) = fs.findOne(MongoDBObject(FILE_POINTER_FIELD -> fileId)) match {
    case Some(thumb) => {

      // update active thumbnail
      fs.findOne(MongoDBObject(THUMBNAIL_OBJECT_POINTER_FIELD -> objectId)) foreach {
        theOldOne =>
          theOldOne.put(THUMBNAIL_OBJECT_POINTER_FIELD, "")
          theOldOne.save
      }
      thumb.put(THUMBNAIL_OBJECT_POINTER_FIELD, objectId)
      thumb.save

      // update active image
      fs.findOne(MongoDBObject(IMAGE_OBJECT_POINTER_FIELD -> objectId)) foreach {
        theOldOne =>
          theOldOne.put(IMAGE_OBJECT_POINTER_FIELD, "")
          theOldOne.save
      }
      fs.findOne(fileId) foreach {
        theNewOne =>
          theNewOne.put(IMAGE_OBJECT_POINTER_FIELD, objectId)
          theNewOne.save
      }

      Some(fileId)
    }
    case None => None
  }

}

case class FileUploadResponse(name: String, size: Long, url: String = "", thumbnail_url: String = "", delete_url: String = "", delete_type: String = "DELETE", error: String = null)