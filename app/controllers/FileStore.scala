package controllers

import com.mongodb.casbah.gridfs.Imports._
import org.bson.types.ObjectId
import play.mvc.results.{RenderBinary, Result}
import play.mvc.Util
import java.io.File
import com.mongodb.casbah.gridfs.{GridFSDBFile, GridFS}
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.MongoConnection
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import user.FileUpload

/**
 * Common controller for handling files, no matter from where.
 *
 * TODO add indexes on the object_id and image_id field
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileStore extends DelvingController {

  RegisterJodaTimeConversionHelpers()

  val emptyThumbnail = "/public/images/dummy-object.png"
  val emptyThumbnailFile = new File(play.Play.applicationPath + emptyThumbnail)

  val DEFAULT_THUMBNAIL_WIDTH = 220

  val THUMBNAIL_WIDTH_FIELD = "thumbnail_width"

  // ~~ images uploaded directly via culturehub
  val UPLOAD_UID_FIELD = "uid" // temporary UID given to files that are not yet attached to an object after upload
  val OBJECT_POINTER_FIELD = "object_id" // pointer to the owning object, for cleanup
  val FILE_POINTER_FIELD = "original_file" // pointer from a thumbnail to its parent file
  val IMAGE_OBJECT_POINTER_FIELD = "image_object_id" // pointer from an chosen image to its object, useful to lookup an image by object ID
  val THUMBNAIL_OBJECT_POINTER_FIELD = "thumbnail_object_id" // pointer from a chosen thumbnail to its object, useful to lookup a thumbnail by object ID

  // ~~ images stored locally (file system)
  val IMAGE_ID_FIELD = "file_id"
  val ORIGIN_PATH_FIELD = "origin_path"


  val fileStore = MongoConnection().getDB(play.Play.configuration.getProperty("db.fileStore.name"))
  val fs = GridFS(fileStore)


  def get(id: String): Result = {
    if (!ObjectId.isValid(id)) return Error("Invalid ID " + id)
    val oid = new ObjectId(id)
    val file = fs.findOne(oid) getOrElse (return NotFound("Could not find file with ID " + id))
    new RenderBinary(file.inputStream, file.filename, file.length, file.contentType, false)
  }

  def displayThumbnail(id: String, width: String = ""): Result = {
    val thumbnailWidth = if (FileUpload.thumbnailSizes.contains(width)) {
      FileUpload.thumbnailSizes(width)
    } else {
      try {
        Integer.parseInt(width)
      } catch {
        case _ => DEFAULT_THUMBNAIL_WIDTH
      }
    }
    renderImage(id, true, thumbnailWidth)
  }

  def displayImage(id: String): Result = renderImage(id, false)

  @Util def renderImage(id: String, thumbnail: Boolean, thumbnailWidth: Int = DEFAULT_THUMBNAIL_WIDTH): Result = {

    val (field, oid) = if (ObjectId.isValid(id)) {
      (if (thumbnail) THUMBNAIL_OBJECT_POINTER_FIELD else IMAGE_OBJECT_POINTER_FIELD, new ObjectId(id))
    } else {
      // string identifier - for e.g. ingested images
      (IMAGE_ID_FIELD, id)
    }

    val query = if (thumbnail) MongoDBObject(field -> oid, THUMBNAIL_WIDTH_FIELD -> thumbnailWidth) else MongoDBObject(field -> oid)

    val image: Option[GridFSDBFile] = fs.findOne(query) match {
      case Some(file) => {
        ImageCacheService.setImageCacheControlHeaders(file, response, 60 * 15)
        Some(file)
      }
      case None if (thumbnail) => {
        // try to find the next fitting size
        fs.find(MongoDBObject(field -> oid)).sortWith((a, b) => a.get(THUMBNAIL_WIDTH_FIELD).asInstanceOf[Int] > b.get(THUMBNAIL_WIDTH_FIELD).asInstanceOf[Int]).headOption match {
          case Some(t) => Some(t)
          case None => return new RenderBinary(emptyThumbnailFile, emptyThumbnailFile.getName, true)
        }
      }
      case None => if (thumbnail) return new RenderBinary(emptyThumbnailFile, emptyThumbnailFile.getName, true) else None

    }
    image match {
      case None => NotFound
      case Some(t) => new RenderBinary(t.inputStream, t.filename, t.length, t.contentType, true)
    }
  }


  @Util def imageExists(objectId: ObjectId) = fs.find(MongoDBObject(IMAGE_OBJECT_POINTER_FIELD -> objectId)).nonEmpty

  @Util def getInputStream(id: ObjectId) = fs.find(id).inputStream

  implicit def otobah(in: Option[com.mongodb.gridfs.GridFSDBFile]): Option[com.mongodb.casbah.gridfs.GridFSDBFile] = {
    in match {
      case Some(i) => Some(wrapDBFile(i))
      case None => None
    }
  }
}