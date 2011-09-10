package controllers

import play.mvc.results.Result
import models.{DObject, UserCollection}
import org.bson.types.ObjectId

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController {

  import views.Collection._

  def list(user: Option[String], query: String, page: Int = 1): AnyRef = {

    val collectionsPage = user match {
      case Some(u) => UserCollection.findByUser(browsedUserId).page(page)
      case None => UserCollection.findAll.page(page)
    }

    html.list(collections = collectionsPage._1 map { c => ShortCollection(c._id, c.name, c.description.getOrElse(""), "", c.userName)}, page = page, count = collectionsPage._2)
  }

  def view(user: String, id: String): AnyRef = {
    UserCollection.findById(id) match {
      case None => NotFound
      case Some(collection) => html.collection(collection)
    }
  }

  /**list all user collections the connected user can write to as tokens **/
  def listWriteableAsTokens(q: String): Result = {
    val userCollections = for (userCollection <- UserCollection.findByUser(connectedUserId).filter(c => c.name.toLowerCase contains(q))) yield Token(userCollection._id.toString, userCollection.name)
    Json(userCollections)
  }

  def listObjects(id: String): Result = {
    if(!ObjectId.isValid(id)) Error("Invalid collection id " + id)
    val cid = new ObjectId(id)
    val objects = for(o <- DObject.findAllWithCollection(cid)) yield ObjectModel(Some(o._id), o.name, o.description, o.user_id)
    
    request.format match {
      case "json" => Json(objects)
      case _ => BadRequest
    }
  }
}

// ~~~ list page models

case class ShortCollection(id: ObjectId, name: String, shortDescription: String, thumbnailUrl: String, userName: String)