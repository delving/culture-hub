package controllers

import com.mongodb.casbah.Imports._
import scala.collection.JavaConversions.asScalaIterable
import org.bson.types.ObjectId
import play.mvc.results.{RenderBinary, Result}
import play.mvc.Util
import com.mongodb.casbah.gridfs.{GridFS}
import models.StoredFile
import java.io.File
import scala.io.Source

/**
 * Common controller for handling files, no matter from where.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileStore extends DelvingController {

  val emptyThumbnail = "/public/images/dummy-object.png"

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

  def getThumbnail(id: String): Result = {
    if (!ObjectId.isValid(id)) return Error("Invalid ID " + id)
    val oid = new ObjectId(id)
    fs.findOne(MongoDBObject("image_id" -> oid)) match {
      case Some(file) => new RenderBinary(file.inputStream, file.filename, file.length, file.contentType, false)
      case None => {
        new RenderBinary(new File(play.Play.applicationPath + emptyThumbnail), "dummy-object.png")
      }
    }

  }

  @Util def makeThumbnail(objectId: ObjectId, fileId: ObjectId, width: Int = 220) = {
    val image = fs.find(fileId)
    val thumbnailStream = ImageCacheService.createThumbnail(image.inputStream, width)
    val thumbnail = fs.createFile(thumbnailStream)
    thumbnail.filename = image.filename
    thumbnail.contentType = "image/jpeg"
    thumbnail.put("image_id", objectId)
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