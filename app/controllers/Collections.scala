package controllers

import play.mvc.results.Result
import org.bson.types.ObjectId
import user.ObjectModel
import models.{Visibility, DObject, UserCollection}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController {

  def list(user: Option[String], page: Int = 1): Result = {

    val collectionsPage = user match {
      case Some(u) => UserCollection.browseByUser(browsedUserId, connectedUserId).page(page)
      case None => UserCollection.browseAll(connectedUserId).page(page)
    }
    val items: List[ListItem] = collectionsPage._1
    Template("/list.html", 'title -> listPageTitle("collection"), 'itemName -> "collection", 'items -> items, 'page -> page, 'count -> collectionsPage._2)
  }

  def collection(user: String, id: String): Result = {
    UserCollection.findByIdUnsecured(id) match {
      case Some(thing) if (thing.visibility == Visibility.PUBLIC || thing.visibility == Visibility.PRIVATE && thing.user_id == connectedUserId) => {
        val objects: List[ListItem] = DObject.findAllWithCollection(thing._id).toList
        Template('collection -> thing, 'objects -> objects)
      }
      case _ => NotFound
    }
  }

  /**list all user collections the connected user can write to as tokens **/
  def listWriteableAsTokens(q: String): Result = {
    val userCollections = for (userCollection <- UserCollection.browseByUser(connectedUserId, connectedUserId).filter(c => c.name.toLowerCase contains (q))) yield Token(userCollection._id.toString, userCollection.name)
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
      if (!ObjectId.isValid(id)) Error(&("collections.invalidCollectionId", id))
      val cid = new ObjectId(id)
      val objects = DObject.findAllWithCollection(cid) map { objectToObjectModel(_) }
      request.format match {
        case "json" => Json(objects)
        case _ => BadRequest
      }
    }
  }
}