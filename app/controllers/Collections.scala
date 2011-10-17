package controllers

import play.mvc.results.Result
import models.{DObject, UserCollection}
import org.bson.types.ObjectId
import user.ObjectModel

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController {

  def list(user: Option[String], query: String, page: Int = 1): Result = {

    val collectionsPage = user match {
      case Some(u) => UserCollection.queryWithUser(query, browsedUserId).page(page)
      case None => UserCollection.queryAll(query).page(page)
    }
    val items: List[ListItem] = collectionsPage._1
    Template("/list.html", 'title -> listPageTitle("collection"), 'itemName -> "collection", 'items -> items, 'page -> page, 'count -> collectionsPage._2)
  }

  def collection(user: String, id: String): Result = {
    UserCollection.findById(id) match {
      case None => NotFound
      case Some(collection) => {
        val objects: List[ListItem] = DObject.findAllWithCollection(collection._id).toList
        Template('collection -> collection, 'objects -> objects)
      }
    }
  }

  /**list all user collections the connected user can write to as tokens **/
  def listWriteableAsTokens(q: String): Result = {
    val userCollections = for (userCollection <- UserCollection.findByUser(connectedUserId).filter(c => c.name.toLowerCase contains (q))) yield Token(userCollection._id.toString, userCollection.name)
    Json(userCollections)
  }

  val NO_COLLECTION = "NO_COLLECTION"

  def listObjects(user: String, id: String): Result = {

    def objectToObjectModel(o: DObject) = ObjectModel(Some(o._id), o.name, o.description, o.user_id)

    // unassigned objects
    if (id == NO_COLLECTION) {
      getUser(user) match {
        case Right(aUser) => Json(DObject.findAllUnassignedForUser(aUser._id) map { objectToObjectModel(_) })
        case Left(error) => error
      }
    } else {
      if (!ObjectId.isValid(id)) Error("Invalid collection id " + id)
      val cid = new ObjectId(id)
      val objects = DObject.findAllWithCollection(cid) map { objectToObjectModel(_) }
      request.format match {
        case "json" => Json(objects)
        case _ => BadRequest
      }
    }
  }
}