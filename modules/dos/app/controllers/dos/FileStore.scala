package controllers.dos

import play.api.mvc._

import org.bson.types.ObjectId
import com.mongodb.gridfs.GridFSDBFile
import com.mongodb.casbah.commons.MongoDBObject
import play.api.libs.iteratee.Enumerator
import core.DomainConfigurationAware
import models.DomainConfiguration

/**
 * Common controller for handling files, no matter from where.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileStore extends Controller with DomainConfigurationAware {

  // ~~~ public HTTP API

  def get(id: String): Action[AnyContent] = DomainConfigured {
    Action {
      implicit request =>
        if (!ObjectId.isValid(id)) BadRequest("Invalid ID " + id)
        val oid = new ObjectId(id)
        val file = fileStore(configuration).findOne(oid) getOrElse (return Action {
          implicit request => NotFound("Could not find file with ID " + id)
        })
        Ok.stream(Enumerator.fromStream(file.inputStream)).withHeaders(
          (CONTENT_DISPOSITION -> ("attachment; filename=" + file.filename)),
          (CONTENT_LENGTH -> file.length.toString),
          (CONTENT_TYPE -> file.contentType))
    }
  }


  // ~~~ public scala API

  def getFilesForItemId(id: String)(implicit configuration: DomainConfiguration): List[StoredFile] =
    fileStore(configuration).
      find(MongoDBObject(ITEM_POINTER_FIELD -> id)).
      map(f => fileToStoredFile(f, configuration)).toList

  // ~~~ private

  private[dos] def fileToStoredFile(f: GridFSDBFile, configuration: DomainConfiguration) = {
    val id = f.getId.asInstanceOf[ObjectId]
    val thumbnail = if (FileUpload.isImage(f)) {
      fileStore(configuration).findOne(MongoDBObject(FILE_POINTER_FIELD -> id)) match {
        case Some(t) => Some(t.id.asInstanceOf[ObjectId])
        case None => None
      }
    } else {
      None
    }
    StoredFile(id, f.getFilename, f.getContentType, f.getLength, thumbnail)
  }
}