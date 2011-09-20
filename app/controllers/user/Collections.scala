package controllers.user

import play.templates.Html
import views.User.Collection._
import play.mvc.results.Result
import org.bson.types.ObjectId
import com.mongodb.WriteConcern
import com.novus.salat.dao.SalatDAOUpdateError
import extensions.CHJson._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import org.scala_tools.time.Imports._
import controllers._
import models.{DObject, UserCollection}

/**
 * Manipulation of user collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController with UserAuthentication with Secure {

  def load(id: String): Result = {
    // TODO access rights
    val allObjects = (DObject.findByUser(browsedUserId).map {o => ObjectModel(Some(o._id), o.name, o.description, o.user_id)}).toList

    UserCollection.findById(id) match {
      case None => Json(CollectionAddModel(allObjects = allObjects, availableObjects = allObjects))
      case Some(col) => {
        val objects = DObject.findAllWithCollection(col._id).toList map { obj => ObjectModel(Some(obj._id), obj.name, obj.description, obj.user_id)}
        Json(CollectionAddModel(id = Some(col._id), name = col.name, description = col.description, allObjects = allObjects, objects = objects, availableObjects = (allObjects filterNot (objects contains)), thumbnail = col.thumbnail_object_id))
      }
    }
  }


  def collectionUpdate(id: String): Html = html.collection(Option(id))

  def collectionSubmit(data: String): Result = {

    val collectionModel: CollectionAddModel = parse[CollectionAddModel](data)

    val persistedUserCollection = collectionModel.id match {
      case None =>
        val inserted: Option[ObjectId] = UserCollection.insert(
          UserCollection(TS_update = DateTime.now,
            name = collectionModel.name,
            node = getNode,
            user_id = connectedUserId,
            userName = connectedUser,
            description = collectionModel.description,
            thumbnail_object_id = collectionModel.thumbnail))
//            access = AccessRight(users = Map(getUserReference.id -> UserAction(user = getUserReference, read = Some(true), update = Some(true), delete = Some(true), owner = Some(true))))))
        if (inserted != None) Some(collectionModel.copy(id = inserted)) else None
      case Some(id) =>
        val existingObject = UserCollection.findOneByID(id)
        if (existingObject == None) Error("Object with id %s not found".format(id))
        val updatedUserCollection = existingObject.get.copy(TS_update = DateTime.now, name = collectionModel.name, description = collectionModel.description, thumbnail_object_id = collectionModel.thumbnail)
        try {
          UserCollection.update(MongoDBObject("_id" -> id), updatedUserCollection, false, false, new WriteConcern())
          Some(collectionModel)
        } catch {
          case e: SalatDAOUpdateError => None
          case _ => None
        }
    }

    persistedUserCollection match {
      case Some(theObject) => {
        val objectIds = for(o <- collectionModel.objects) yield o.id.get
        DObject.update(("_id" $in objectIds), ($addToSet ("collections" -> theObject.id.get)), false, true)
        Json(theObject)
      }
      case None => Error("Error saving object")
    }
  }

  // TODO move someplace generic
  implicit def stringToObjectIdOption(id: String): Option[ObjectId] = if(!ObjectId.isValid(id)) None else Some(new ObjectId(id))
  implicit def objectIdOptionToString(id: Option[ObjectId]): String = id match {
    case None => ""
    case Some(oid) => oid.toString
  }
}

case class CollectionAddModel(id: Option[ObjectId] = None,
                              name: String = "",
                              description: Option[String] = Some(""),
                              objects: List[ObjectModel] = List.empty[ObjectModel],
                              allObjects: List[ObjectModel] = List.empty[ObjectModel],
                              availableObjects: List[ObjectModel] = List.empty[ObjectModel],
                              thumbnail: String = "")