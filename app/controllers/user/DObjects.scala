package controllers.user

import play.templates.Html
import views.User.Object._
import play.mvc.results.Result
import extensions.CHJson._
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.SalatDAOUpdateError
import org.scala_tools.time.Imports._
import play.libs.Codec
import controllers._
import models.{Label, DObject}
import com.mongodb.WriteConcern
import com.mongodb.casbah.Imports._

/**
 * Controller for manipulating user objects (creation, update, ...)
 * listing and display is done in the other controller that does not require authentication
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DObjects extends DelvingController with UserAuthentication with Secure {

  def objectUpdate(id: String): Html = html.add(Option(id), Codec.UUID())

  def objectSubmit(data: String, uid: String): Result = {
    val objectModel: ObjectModel = parse[ObjectModel](data)
    val files = FileStore.fetchFilesForUID(uid)

    def makeThumbnail(objectId: ObjectId) = findThumbnailCandidate(files) match {
        case Some(f) => FileStore.makeThumbnail(objectId, f.id)
        case None => None
    }

    val labels = {
      for(l <- objectModel.labels) yield {
        Label.findOne(MongoDBObject("labelType" -> l.labelType, "value" -> l.value)) match {
          case Some(label) => label._id
          // TODO better error handling
          case None => Label.insert(models.Label(user_id = connectedUserId, userName = connectedUser, labelType = l.labelType, value = l.value)).get
        }
      }
    }

    val persistedObject = objectModel.id match {
      case None =>
        val inserted: Option[ObjectId] = DObject.insert(DObject(TS_update = DateTime.now, name = objectModel.name, description = objectModel.description, user_id = connectedUserId, userName = connectedUser, collections = objectModel.getCollections, files = files, labels = labels))
        inserted match {
          case Some(iid) => {
            makeThumbnail(iid) foreach { thumb => DObject.updateThumbnail(iid, thumb) }
            Some(objectModel.copy(id = inserted))
          }
          case None => None
        }
      case Some(id) =>
        val existingObject = DObject.findOneByID(id)
        if(existingObject == None) Error("Object with id %s not found".format(id))
        val updatedObject = existingObject.get.copy(TS_update = DateTime.now, name = objectModel.name, description = objectModel.description, user_id = connectedUserId, collections = objectModel.getCollections, files = existingObject.get.files ++ files, labels = labels, thumbnail_file_id = makeThumbnail(id))
        try {
          DObject.update(MongoDBObject("_id" -> id), updatedObject, false, false, new WriteConcern())
          makeThumbnail(id)
          Some(objectModel)
        } catch {
          case e: SalatDAOUpdateError => None
          case _ => None
        }
    }

    persistedObject match {
      case Some(theObject) => {
        FileStore.detachFilesForUID(uid)
        Json(theObject)
      }
      case None => Error("Error saving object")
    }
  }

}