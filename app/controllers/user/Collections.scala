package controllers.user

import play.mvc.results.Result
import org.bson.types.ObjectId
import com.mongodb.WriteConcern
import com.novus.salat.dao.SalatDAOUpdateError
import extensions.JJson._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import controllers._
import play.data.validation.Annotations._
import java.util.Date
import models.{Visibility, DObject, UserCollection}

/**
 * Manipulation of user collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController with UserSecured {

  def load(id: String): Result = {
    val allObjects = (DObject.browseByUser(browsedUserId, connectedUserId).map {o => ObjectModel(Some(o._id), o.name, o.description, o.user_id)}).toList

    UserCollection.findById(id, connectedUserId) match {
      case None => Json(CollectionViewModel(allObjects = allObjects, availableObjects = allObjects))
      case Some(col) => {
        val objects = DObject.findAllWithCollection(col._id).toList map { obj => ObjectModel(Some(obj._id), obj.name, obj.description, obj.user_id)}
        Json(CollectionViewModel(id = Some(col._id), name = col.name, description = col.description, allObjects = allObjects, objects = objects, availableObjects = (allObjects filterNot (objects contains)), thumbnail = col.thumbnail_id))
      }
    }
  }


  def collection(id: String): Result = {
    renderArgs += ("viewModel", classOf[CollectionViewModel])
    Template('id -> Option(id))
  }

  def collectionSubmit(data: String): Result = {

    val collectionModel: CollectionViewModel = parse[CollectionViewModel](data)
    validate(collectionModel).foreach { errors => return JsonBadRequest(collectionModel.copy(errors = errors)) }

    val persistedUserCollection: Option[CollectionViewModel] = collectionModel.id match {
      case None =>
        val inserted: Option[ObjectId] = UserCollection.insert(
          UserCollection(TS_update = new Date(),
            name = collectionModel.name,
            user_id = connectedUserId,
            userName = connectedUser,
            description = collectionModel.description,
            visibility = Visibility.get(collectionModel.visibility),
            thumbnail_id = collectionModel.thumbnail))
        if (inserted != None) Some(collectionModel.copy(id = inserted)) else None
      case Some(id) =>
        val existingCollection = UserCollection.findOneByID(id)
        if (existingCollection == None) Error(&("user.collections.objectNotFound", id))
        val updatedUserCollection = existingCollection.get.copy(TS_update = new Date(), name = collectionModel.name, description = collectionModel.description, thumbnail_id = collectionModel.thumbnail, visibility = Visibility.get(collectionModel.visibility))
        try {
          UserCollection.update(MongoDBObject("_id" -> id), updatedUserCollection, false, false, new WriteConcern())
          Some(collectionModel)
        } catch {
          case e: SalatDAOUpdateError => None
          case _ => None
        }
    }

    persistedUserCollection match {
      case Some(theCollection) => {
        val objectIds = for(o <- collectionModel.objects) yield o.id.get
        DObject.update(("_id" $in objectIds), ($addToSet ("collections" -> theCollection.id.get)), false, true)
        Json(theCollection)
      }
      case None => Error(&("user.collections.saveError", collectionModel.name))
    }

  }

  def remove(id: ObjectId) = {
    if(UserCollection.owns(connectedUserId, id)) UserCollection.delete(id) else Forbidden("Big brother is watching you")
  }



  // TODO move someplace generic
  implicit def stringToObjectIdOption(id: String): Option[ObjectId] = if(!ObjectId.isValid(id)) None else Some(new ObjectId(id))
  implicit def objectIdOptionToString(id: Option[ObjectId]): String = id match {
    case None => ""
    case Some(oid) => oid.toString
  }
}

case class CollectionViewModel(id: Option[ObjectId] = None,
                              @Required name: String = "",
                              @Required description: String = "",
                              objects: List[ObjectModel] = List.empty[ObjectModel],
                              allObjects: List[ObjectModel] = List.empty[ObjectModel],
                              availableObjects: List[ObjectModel] = List.empty[ObjectModel],
                              visibility: Int = Visibility.PUBLIC.value,
                              thumbnail: String = "",
                              errors: Map[String, String] = Map.empty[String, String]) extends ViewModel