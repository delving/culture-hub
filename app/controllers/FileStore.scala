package controllers

import com.mongodb.casbah.Imports._
import scala.collection.JavaConversions.asScalaIterable
import org.bson.types.ObjectId
import play.mvc.results.{RenderBinary, Result}
import play.mvc.Util
import com.mongodb.casbah.gridfs.GridFS
import models.StoredFile
import java.io.File

/**
 * Common controller for handling files, no matter from where.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileStore extends DelvingController {

  val emptyThumbnail = "/public/images/dummy-object.png"
  val emptyThumbnailFile = new File(play.Play.applicationPath + emptyThumbnail)

  val IMAGE_FIELD = "image_object_id"
  val THUMBNAIL_FIELD = "thumbnail_object_id"

  val fileStore = MongoConnection().getDB("fileStore")
  val fs = GridFS(fileStore)

  def uploadFile(uid: String): Result = {
    val uploads = request.args.get("__UPLOADS").asInstanceOf[java.util.List[play.data.Upload]]

    val uploadedFiles = for (upload: play.data.Upload <- asScalaIterable(uploads)) yield {
      val f = fs.createFile(upload.asStream())
      f.filename = upload.getFileName
      f.contentType = upload.getContentType
      f.put("uid", uid)
      f.save
      FileUploadResponse(upload.getFileName, upload.getSize.longValue())
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
    val field = if(thumbnail) THUMBNAIL_FIELD else IMAGE_FIELD
    fs.findOne(MongoDBObject(field -> oid)) match {
      case Some(file) => {
        ImageCacheService.setImageCacheControlHeaders(file, response, 60 * 15)
        new RenderBinary(file.inputStream, file.filename, file.length, file.contentType, true)
      }
      case None => {
        if(thumbnail) new RenderBinary(emptyThumbnailFile, emptyThumbnailFile.getName, true) else NotFound
      }
    }
  }

  /** makes a thumbnail for the given (image) file and marks it as The Chosen One **/
  @Util def makeThumbnail(objectId: ObjectId, fileId: ObjectId, width: Int = 220) = {
    // TODO add indexes on the object_id and image_id field
    val image = fs.find(fileId)
    image.put(IMAGE_FIELD, objectId)
    image.save
    val thumbnailStream = ImageCacheService.createThumbnail(image.inputStream, width)
    val thumbnail = fs.createFile(thumbnailStream)
    thumbnail.filename = image.filename
    thumbnail.contentType = "image/jpeg"
    thumbnail.put(THUMBNAIL_FIELD, objectId)
    thumbnail.save
    thumbnail._id
  }

  @Util def getInputStream(id: ObjectId) = fs.find(id).inputStream

  @Util def fetchFilesForUID(uid: String): Seq[StoredFile] = fs.find(MongoDBObject("uid" -> uid)) map {
    f => StoredFile(f.getId.asInstanceOf[ObjectId], f.getFilename, f.getContentType, f.getLength)
  }

  @Util def detachFilesForUID(uid: String) {
    fs.find(MongoDBObject("uid" -> uid)) map {
      f =>
        // yo listen up, this ain't implemented in the mongo driver and throws an UnsupportedOperationException
        // f.removeField("uid")
        f.put("uid", "")
        f.save()
    }
  }
}

case class FileUploadResponse(name: String, size: Long, url: String = "", thumbnail_url: String = "", delete_url: String = "", delete_type: String = "DELETE", error: String = null)