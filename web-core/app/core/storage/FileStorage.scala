package core.storage

import models.DomainConfiguration
import com.mongodb.casbah.Imports._
import com.mongodb.gridfs.{GridFSFile, GridFSDBFile}
import models.HubMongoContext._

/**
 * Collection of methods for dealing with files and file uploads.
 * These methods have been savagly ripped out of the DoS because they are needed in web-core as well.
 *
 * TODO design a service for storage, and a service for upload, then refactor
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object FileStorage {

  def getFilesForItemId(id: String)(implicit configuration: DomainConfiguration): List[StoredFile] =
    fileStore(configuration).
      find(MongoDBObject(ITEM_POINTER_FIELD -> id)).
      map(f => fileToStoredFile(f)).toList

  def deleteFilesForItemId(id: String)(implicit configuration: DomainConfiguration) {
    val files = FileStorage.getFilesForItemId(id)
    files.foreach { f => FileStorage.deleteFileById(f.id) }
  }

  def getFilesForUID(uid: String)(implicit configuration: DomainConfiguration): Seq[StoredFile] =
    fileStore(configuration).
      find(MongoDBObject(UPLOAD_UID_FIELD -> uid)).
      map(f => fileToStoredFile(f)).
      toSeq

  /**
   * Attaches all files to an object, given the upload UID
   */
  def markFilesAttached(uid: String, objectIdentifier: String)(implicit configuration: DomainConfiguration) {
    fileStore(configuration).find(MongoDBObject(UPLOAD_UID_FIELD -> uid)) map {
      f =>
      // yo listen up, this ain't implemented in the mongo driver and throws an UnsupportedOperationException
      // f.removeField("uid")
        f.put(UPLOAD_UID_FIELD, "")
        f.put(ITEM_POINTER_FIELD, objectIdentifier)
        f.save()
    }
  }

  /**
   * Attaches a single file to an object given the file ID, and resets the UID
   */
  def markSingleFileAttached(fileId: ObjectId, objectIdentifier: String)(implicit configuration: DomainConfiguration) {
    fileStore(configuration).find(MongoDBObject("_id" -> fileId)) map { f =>
      f.put(UPLOAD_UID_FIELD, "")
      f.put(ITEM_POINTER_FIELD, objectIdentifier)
      f.save()
    }
  }

  /**
   * Deletes a single file
   * @param id the mongo id of the
   */
  def deleteFileById(id: ObjectId)(implicit configuration: DomainConfiguration) {
    fileStore(configuration).find(id) foreach {
      toDelete =>
      // remove thumbnails
        fileStore(configuration).find(MongoDBObject(FILE_POINTER_FIELD -> id)) foreach {
          t =>
            fileStore(configuration).remove(t.getId.asInstanceOf[ObjectId])
        }
        // remove the file itself
        fileStore(configuration).remove(id)
    }
  }

  private def fileToStoredFile(f: GridFSDBFile)(implicit configuration: DomainConfiguration) = {
    val id = f.getId.asInstanceOf[ObjectId]
    StoredFile(id, f.getFilename, f.getContentType, f.getLength)
  }

}


case class StoredFile(id: ObjectId, name: String, contentType: String, length: Long)


/**
 * Represents a response to a file upload via the jQuery File Upload widget
 *
 * http://blueimp.github.com/jQuery-File-Upload/
 */
case class FileUploadResponse(
  name: String,
  size: Long,
  url: String = "",
  thumbnail_url: String = "",
  delete_url: String = "",
  delete_type: String = "DELETE",
  error: String = "",
  id: String = ""
)

object FileUploadResponse {

  def apply(file: StoredFile)(implicit configuration: DomainConfiguration): FileUploadResponse = {

    def hasThumbnail(f: StoredFile) = file.contentType.contains("image") || file.contentType.contains("pdf")

    def thumbnailUrl(implicit configuration: DomainConfiguration) = if (hasThumbnail(file)) {
      fileStore(configuration).findOne(MongoDBObject(FILE_POINTER_FIELD -> file.id)) match {
        case Some(t) => Some(t.id.asInstanceOf[ObjectId])
        case None => None
      }
    } else {
      None
    }.map { thumbnailId =>
      "thumbnail/" + thumbnailId.toString
    }

    FileUploadResponse(
      name = file.name,
      size = file.length,
      url = "/file/" + file.id,
      thumbnail_url = thumbnailUrl + "/80",
      delete_url = "/file/" + file.id,
      id = file.id.toString
    )
  }

}