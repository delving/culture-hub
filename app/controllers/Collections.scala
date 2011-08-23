package controllers

import play.mvc.results.Result
import org.bson.types.ObjectId
import models.{DObject, UserCollection}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController {

  import views.Collection._
  
  def list(user: String): AnyRef = {
    val userObject = getUser(user)
    html.list(user = userObject)
  }

  def collection(user: String, collection: String): AnyRef = {
    val u = getUser(user)
    html.collection(user = u, name = collection)
  }

  def load(id: String): Result = {
    UserCollection.findById(id) match {
        case None => Json(CollectionModel.empty)
        case Some(col) => {
          Json(CollectionModel(Some(col._id), col.name, col.description))
        }
      }
  }

  /** list all user collections the connected user can write to as tokens **/
  def listWriteableAsTokens: Result = {
    val userCollections = for(userCollection <- UserCollection.findAllWriteable(getUserReference)) yield Token(userCollection._id.toString, userCollection.name)
    Json(userCollections)
  }
}

case class CollectionModel(id: Option[ObjectId] = None, name: String, description: Option[String] = Some(""))

object CollectionModel {
  val empty = CollectionModel(name = "")

}