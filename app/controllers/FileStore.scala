package controllers

import com.mongodb.casbah.Imports._
import scala.collection.JavaConversions.asScalaIterable
import org.bson.types.ObjectId
import play.mvc.results.{RenderBinary, Result}
import play.mvc.Util
import models.StoredFile
import java.io.File
import com.mongodb.casbah.gridfs.{GridFSDBFile, GridFS}
import com.mongodb.gridfs.GridFSFile

/**
 * Common controller for handling files, no matter from where.
 *
 * TODO add indexes on the object_id and image_id field
 * TODO access control for uploading!!!
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileStore extends DelvingController {

  val emptyThumbnail = "/public/images/dummy-object.png"
  val emptyThumbnailFile = new File(play.Play.applicationPath + emptyThumbnail)

  val THUMBNAIL_WIDTH = 220

  val UPLOAD_UID_FIELD = "uid" // temporary UID given to files that are not yet attached to an object after upload
  val FILE_POINTER_FIELD = "original_file" // pointer from a thumbnail to its parent
  val IMAGE_OBJECT_POINTER_FIELD = "image_object_id" // pointer from an image to its object, useful to lookup an image by object ID
  val THUMBNAIL_OBJECT_POINTER_FIELD = "thumbnail_object_id" // pointer from a thumbnail to its object, useful to lookup a thumbnail by object ID

  val fileStore = MongoConnection().getDB("fileStore")
  val fs = GridFS(fileStore)

  def uploadFile(uid: String): Result = {
    val uploads = request.args.get("__UPLOADS").asInstanceOf[java.util.List[play.data.Upload]]

    val uploadedFiles = for (upload: play.data.Upload <- asScalaIterable(uploads)) yield {
      val f = fs.createFile(upload.asStream())
      f.filename = upload.getFileName
      f.contentType = upload.getContentType
      f.put(UPLOAD_UID_FIELD, uid)
      f.save

      if (f._id == None) return Error("Error saving uploaded file")

      // if this is an image, create a thumbnail for it so we can display it on the fly
      val thumbnailUrl: String = if (f.contentType.contains("image")) {
        fs.findOne(f._id.get) match {
          case Some(storedFile) => createThumbnail(storedFile, THUMBNAIL_WIDTH) match {
            case Some(thumb) => "/file/" + thumb
            case None => emptyThumbnail
          }
          case None => ""
        }
      } else ""

      FileUploadResponse(upload.getFileName, upload.getSize.longValue(), "/file/" + f._id.get, thumbnailUrl)
    }

    Json(uploadedFiles)
  }

  def get(id: String): Result = {
    if (!ObjectId.isValid(id)) return Error("Invalid ID " + id)
    val oid = new ObjectId(id)
    val file = fs.findOne(oid) getOrElse (return NotFound("Could not find file with ID " + id))
    new RenderBinary(file.inputStream, file.filename, file.length, file.contentType, false)
  }

  def getThumbnail(id: String): Result = getImage(id, true)

  def getImage(id: String): Result = getImage(id, false)

  def getImage(id: String, thumbnail: Boolean): Result = {
    if (!ObjectId.isValid(id)) return Error("Invalid ID " + id)
    val oid = new ObjectId(id)
    val field = if (thumbnail) THUMBNAIL_OBJECT_POINTER_FIELD else IMAGE_OBJECT_POINTER_FIELD
    fs.findOne(MongoDBObject(field -> oid)) match {
      case Some(file) => {
        ImageCacheService.setImageCacheControlHeaders(file, response, 60 * 15)
        new RenderBinary(file.inputStream, file.filename, file.length, file.contentType, true)
      }
      case None => {
        if (thumbnail) new RenderBinary(emptyThumbnailFile, emptyThumbnailFile.getName, true) else NotFound
      }
    }
  }

  /**creates a thumbnail and stores a pointer to the original image **/
  def createThumbnail(image: GridFSDBFile, width: Int) = {
    val thumbnailStream = ImageCacheService.createThumbnail(image.inputStream, width)
    val thumbnail = fs.createFile(thumbnailStream)
    thumbnail.filename = image.filename
    thumbnail.contentType = "image/jpeg"
    thumbnail.put(FILE_POINTER_FIELD, image._id)
    thumbnail.save
    thumbnail._id
  }

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

  @Util def getInputStream(id: ObjectId) = fs.find(id).inputStream

  @Util def fetchFilesForUID(uid: String): Seq[StoredFile] = fs.find(MongoDBObject("uid" -> uid)) map {
    f => {
      val id = f.getId.asInstanceOf[ObjectId]
      val thumbnail = if (isImage(f)) {
        fs.findOne(MongoDBObject(FILE_POINTER_FIELD -> id)) match {
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
        f.put(UPLOAD_UID_FIELD, "")
        f.save()
    }
  }

  @Util def isImage(f: GridFSFile) = f.getContentType.contains("image")
}

case class FileUploadResponse(name: String, size: Long, url: String = "", thumbnail_url: String = "", delete_url: String = "", delete_type: String = "DELETE", error: String = null)