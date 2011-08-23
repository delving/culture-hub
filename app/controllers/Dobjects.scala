package controllers

import play.mvc.results.Result
import org.bson.types.ObjectId
import play.mvc.Util
import com.mongodb.casbah.commons.MongoDBObject
import models.{UserCollection, Object, UserReference}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object Dobjects extends DelvingController {

  import views.Dobject._

  def list(user: String): AnyRef = {
    val u = getUser(user)
    html.list(user = u)
  }

  def view(user: String, id: String): AnyRef = {
    loadObject(id) match {
        case None => NotFound
        case Some(anObject) => html.dobject(dobject = anObject)
      }
  }

  def load(id: String): Result = {
    loadObject(id) match {
        case None => Json(ObjectModel.empty)
        case Some(anObject) => {
          val collections = ObjectModel.objectIdListToCollections(anObject.collections)
          Json(ObjectModel(Some(anObject._id), anObject.name, anObject.description, anObject.user, collections))
        }
      }
  }

  @Util def loadObject(id: String): Option[Object] = {
    id match {
      case null => None
      case objectId if !ObjectId.isValid(objectId) => None
      case objectId => models.Object.findOneByID(new ObjectId(id)) // TODO access rights
    }
  }
}

case class ObjectModel(id: Option[ObjectId] = None, name: String = "", description: Option[String] = Some(""), owner: UserReference, collections: List[Collection]) {
  def getCollections: List[ObjectId] = for(collection <- collections) yield new ObjectId(collection.id)
}

object ObjectModel {

  val empty: ObjectModel = ObjectModel(name = "", owner = UserReference("", ""), collections = List())

  def objectIdListToCollections(collectionIds: List[ObjectId]) = {
    (for (userCollection: UserCollection <- UserCollection.find(MongoDBObject("_id" -> MongoDBObject("$in" -> collectionIds)), MongoDBObject("_id" -> 1, "name" -> 1)))
    yield Collection(userCollection._id.toString, userCollection.name)).toList
  }

}

case class Collection(id: String, name: String)
