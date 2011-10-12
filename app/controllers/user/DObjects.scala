package controllers.user

import play.templates.Html
import views.User.Object._
import play.mvc.results.Result
import extensions.CHJson._
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.SalatDAOUpdateError
import play.libs.Codec
import controllers._
import com.mongodb.WriteConcern
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import models.{Visibility, UserCollection, Label, DObject}
import play.data.validation.Annotations._
import java.util.Date

/**
 * Controller for manipulating user objects (creation, update, ...)
 * listing and display is done in the other controller that does not require authentication
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DObjects extends DelvingController with UserAuthentication with Secure {

  implicit val viewModel = Some(classOf[ObjectModel])

  def load(id: String): Result = {
    val availableCollections = UserCollection.findByUser(connectedUserId).toList map { c => CollectionReference(c._id, c.name) }
    DObject.findById(id) match {
        case None => Json(ObjectModel(availableCollections = availableCollections))
        case Some(anObject) => {
          Json(ObjectModel(
            Some(anObject._id),
            anObject.name,
            anObject.description,
            anObject.user_id,
            anObject.visibility.toString,
            anObject.collections,
            availableCollections,
            (Label.findAllWithIds(anObject.labels) map {l => ShortLabel(l.labelType, l.value) }).toList,
            anObject.files map {f => FileUploadResponse(f.name, f.length, "/file/" + f.id, f.thumbnailUrl, "/file/" + f.id)}))
        }
      }
  }


  def objectUpdate(id: String): Html = html.dobject(Option(id), Codec.UUID())

  def objectSubmit(data: String, uid: String): Result = {
    val objectModel: ObjectModel = parse[ObjectModel](data)
    validate(objectModel).foreach { errors => return JsonBadRequest(objectModel.copy(errors = errors)) }

    val files = user.FileUpload.fetchFilesForUID(uid)

    /** finds thumbnail candidate for an object, "activate" thumbnails (for easy lookup) and returns the OID of the thumbnail candidate image file **/
    def activateThumbnail(objectId: ObjectId) = findThumbnailCandidate(files) match {
        case Some(f) => FileUpload.activateThumbnails(f.id, objectId); Some(f.id)
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
        val inserted: Option[ObjectId] = DObject.insert(DObject(TS_update = new Date(), name = objectModel.name, description = objectModel.description, user_id = connectedUserId, userName = connectedUser, collections = objectModel.collections, files = files, labels = labels))
        inserted match {
          case Some(iid) => {
            FileUpload.markFilesAttached(uid, iid)
            activateThumbnail(iid) foreach { thumb => DObject.updateThumbnail(iid, thumb) }
            Some(objectModel.copy(id = inserted))
          }
          case None => None
        }
      case Some(id) =>
        val existingObject = DObject.findOneByID(id)
        if(existingObject == None) Error("Object with id %s not found".format(id))
        val updatedObject = existingObject.get.copy(TS_update = new Date(), name = objectModel.name, description = objectModel.description, visibility = Visibility.withName(objectModel.visibility), user_id = connectedUserId, collections = objectModel.collections, files = existingObject.get.files ++ files, labels = labels, thumbnail_file_id = activateThumbnail(id))
        try {
          DObject.update(MongoDBObject("_id" -> id), updatedObject, false, false, new WriteConcern())
          FileUpload.markFilesAttached(uid, id)
          Some(objectModel)
        } catch {
          case e: SalatDAOUpdateError => None
          case _ => None
        }
    }

    persistedObject match {
      case Some(theObject) => {
        Json(theObject)
      }
      case None => Error("Error saving object")
    }
  }
}

// ~~~ view models

case class ObjectModel(id: Option[ObjectId] = None,
                       @Required name: String = "",
                       description: Option[String] = Some(""),
                       owner: ObjectId = new ObjectId(),
                       visibility: String = "Private",
                       collections: List[ObjectId] = List.empty[ObjectId],
                       availableCollections: List[CollectionReference] = List.empty[CollectionReference],
                       labels: List[ShortLabel] = List.empty[ShortLabel],
                       files: Seq[FileUploadResponse] = Seq.empty[FileUploadResponse],
                       errors: Map[String, String] = Map.empty[String, String]) extends ViewModel

