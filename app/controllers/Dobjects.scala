package controllers

import play.mvc.results.Result
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject
import models.{DObject, UserCollection, UserReference}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object DObjects extends DelvingController {

  import views.Dobject._

  def list(user: String): AnyRef = {
    val u = getUser(user)
    html.list(user = u)
  }

  def view(user: String, id: String): AnyRef = {
    DObject.findById(id) match {
        case None => NotFound
        case Some(anObject) => html.dobject(dobject = anObject)
      }
  }

  def load(id: String): Result = {
    DObject.findById(id) match {
        case None => Json(ObjectModel.empty)
        case Some(anObject) => {
          val collections = ObjectModel.objectIdListToCollections(anObject.collections)
          Json(ObjectModel(Some(anObject._id), anObject.name, anObject.description, anObject.user, collections))
        }
      }
  }
}

case class ObjectModel(id: Option[ObjectId] = None, name: String = "", description: Option[String] = Some(""), owner: ObjectId, collections: List[Collection]) {
  def getCollections: List[ObjectId] = for(collection <- collections) yield new ObjectId(collection.id)
}

object ObjectModel {

  val empty: ObjectModel = ObjectModel(name = "", owner = new ObjectId(), collections = List())

  def objectIdListToCollections(collectionIds: List[ObjectId]) = {
    (for (userCollection: UserCollection <- UserCollection.find(MongoDBObject("_id" -> MongoDBObject("$in" -> collectionIds))))
    yield Collection(userCollection._id.toString, userCollection.name)).toList
  }

}

case class Collection(id: String, name: String)
