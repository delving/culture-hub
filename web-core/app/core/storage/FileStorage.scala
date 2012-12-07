package core.storage

import models.DomainConfiguration
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.Imports._
import models.HubMongoContext._
import java.io.{FileOutputStream, File, InputStream}
import core.{CultureHubPlugin, FileStoreService}
import org.bson.types.ObjectId
import core.messages.FileStored
import org.apache.commons.io.IOUtils

/**
 * Collection of methods for dealing with files and file uploads.
 * These methods have been savagly ripped out of the DoS because they are needed in web-core as well.
 *
 * TODO method to retrieve parameters that are not mongoDB params into StoredFile
 * TODO use fileStore method in other places when it makes sense
 * TODO replace / inline all old method calls
 * TODO decouple cleanup of file derivates (thumbnails) from deletion of a file here. Perhaps via event broadcasting
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object FileStorage extends FileStoreService {

  def listFiles(bucketId: String, fileType: Option[String] = None)(implicit configuration: DomainConfiguration): List[StoredFile] = {
    val query = MongoDBObject(ITEM_POINTER_FIELD -> bucketId) ++ fileType.map(t => MongoDBObject(ITEM_TYPE -> t)).getOrElse(MongoDBObject())
    fileStore(configuration).
      find(query).
      map(f => fileToStoredFile(f)).toList
  }


  def deleteFiles(bucketId: String, fileType: Option[String] = None)(implicit configuration: DomainConfiguration) {
    val files = listFiles(bucketId, fileType)
    files.foreach { f =>
      FileStorage.deleteFile(f.id.toString)
    }
  }

  def storeFile(file: File, contentType: String, fileName: String, bucketId: String, fileType: Option[String] = None,
                params: Map[String, AnyRef] = Map.empty, advertise: Boolean = true)(implicit configuration: DomainConfiguration): Option[StoredFile] = {
    val f = fileStore(configuration).createFile(file)
    f.filename = fileName
    f.contentType = contentType
    f.put(ITEM_POINTER_FIELD, bucketId)
    fileType.foreach { t =>
      f.put(ITEM_TYPE, t)
    }
    f.save()

    if (advertise) {
      CultureHubPlugin.broadcastMessage(
        FileStored(bucketId, f.id.toString, fileType, fileName, contentType, configuration)
      )
    }

    fileStore(configuration).findOne(f._id.get).map { f =>
      fileToStoredFile(f)
    }
  }

  def retrieveFile(fileIdentifier: String)(implicit configuration: DomainConfiguration): Option[StoredFile] = {
    if (ObjectId.isValid(fileIdentifier)) {
      fileStore(configuration).findOne(new ObjectId(fileIdentifier)).map { file =>
        StoredFile(file._id.get, file.filename.getOrElse(""), file.contentType.getOrElse("unknown/unknown"), file.size, file.inputStream)
      }
    } else {
      None
    }
  }

  def deleteFile(fileIdentifier: String)(implicit configuration: DomainConfiguration) {
    if (ObjectId.isValid(fileIdentifier)) {
      val id = new ObjectId(fileIdentifier)
      fileStore(configuration).find(id) foreach { toDelete =>
        // remove thumbnails
        fileStore(configuration).find(MongoDBObject(FILE_POINTER_FIELD -> id)) foreach { t =>
            fileStore(configuration).remove(t.getId.asInstanceOf[ObjectId])
        }
        // remove the file itself
        fileStore(configuration).remove(id)
    }
    }
  }

  def renameBucket(oldBucketId: String, newBucketId: String)(implicit configuration: DomainConfiguration) {
    val files: Seq[com.mongodb.gridfs.GridFSDBFile] = fileStore(configuration).find(MongoDBObject(ITEM_POINTER_FIELD -> oldBucketId))
    files.foreach { file =>
      file.put(ITEM_POINTER_FIELD, newBucketId.asInstanceOf[AnyRef])
      file.save()
    }
  }

  def setFileType(newFileType: Option[String], files: Seq[StoredFile])(implicit configuration: DomainConfiguration) {
    files.foreach { f =>
      fileStore(configuration).findOne(f.id).map { file =>
        newFileType.map { t =>
          file.put(ITEM_TYPE, t)
        }.getOrElse {
          file.remove(ITEM_TYPE)
        }
        file.save()
      }
    }
  }

  /**
   * Attaches all files to an object, given the upload UID
   *
   * TODO this method is now part of controllers.dos.FileUpload and needs to be removed here once we are done with refactoring
   */
  def markFilesAttached(uid: String, objectIdentifier: String)(implicit configuration: DomainConfiguration) {
    val files = FileStorage.listFiles(uid, Some(FILE_TYPE_UNATTACHED))
    FileStorage.renameBucket(uid, objectIdentifier)
    FileStorage.setFileType(None, files)
  }

  private def fileToStoredFile(f: GridFSDBFile)(implicit configuration: DomainConfiguration) = {
    val id = f._id.get
    StoredFile(id, f.filename.getOrElse(""), f.contentType.getOrElse("unknown/unknown"), f.size, f.inputStream)
  }

}


case class StoredFile(id: ObjectId, name: String, contentType: String, length: Long, content: InputStream) {

  def writeTo(file: File) {
    val os = new FileOutputStream(file)
    try {
      IOUtils.copy(content, os)
      os.flush()
    } finally {
      os.close()
    }
  }

}


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