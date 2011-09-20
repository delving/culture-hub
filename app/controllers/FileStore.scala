package controllers

import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import play.mvc.results.{RenderBinary, Result}
import play.mvc.Util
import java.io.File
import com.mongodb.casbah.gridfs.GridFS

/**
 * Common controller for handling files, no matter from where.
 *
 * TODO add indexes on the object_id and image_id field
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileStore extends DelvingController {

  val emptyThumbnail = "/public/images/dummy-object.png"
  val emptyThumbnailFile = new File(play.Play.applicationPath + emptyThumbnail)

  val THUMBNAIL_WIDTH = 220

  val UPLOAD_UID_FIELD = "uid" // temporary UID given to files that are not yet attached to an object after upload
  val OBJECT_POINTER_FIELD = "object_id" // pointer to the owning object, for cleanup
  val FILE_POINTER_FIELD = "original_file" // pointer from a thumbnail to its parent
  val IMAGE_OBJECT_POINTER_FIELD = "image_object_id" // pointer from an chosen image to its object, useful to lookup an image by object ID
  val THUMBNAIL_OBJECT_POINTER_FIELD = "thumbnail_object_id" // pointer from a chosen thumbnail to its object, useful to lookup a thumbnail by object ID

  val fileStore = MongoConnection().getDB(play.Play.configuration.getProperty("db.fileStore.name"))
  val fs = GridFS(fileStore)


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

  @Util def imageExists(objectId: ObjectId) = fs.find(MongoDBObject(IMAGE_OBJECT_POINTER_FIELD -> objectId)).nonEmpty

  @Util def getInputStream(id: ObjectId) = fs.find(id).inputStream


}