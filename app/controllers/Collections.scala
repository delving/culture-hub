package controllers

import play.mvc.results.Result
import org.bson.types.ObjectId
import user.ObjectModel
import models.{Visibility, DObject, UserCollection}
import extensions.JJson
import util.Constants._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController {

  def list(user: Option[String], page: Int = 1): Result = {
    val browser: (List[ListItem], Int) = Search.browse(USERCOLLECTION, user, request, theme)
    Template("/list.html", 'title -> listPageTitle("collection"), 'itemName -> "collection", 'items -> browser._1, 'page -> page, 'count -> browser._2)
  }

  def collection(user: String, id: String): Result = {
    UserCollection.findByIdUnsecured(id) match {
      case Some(thing) if (thing.visibility == Visibility.PUBLIC || thing.visibility == Visibility.PRIVATE && thing.user_id == connectedUserId) => {
        val objects: List[ListItem] = DObject.findAllWithCollection(thing._id).toList
        val labels: List[Token] = thing.freeTextLinks
        val places: List[Token] = thing.placeLinks
        Template('collection -> thing, 'objects -> objects, 'labels -> JJson.generate(labels), 'labelsList -> labels, 'places -> JJson.generate(places), 'placesList -> places)
      }
      case _ => NotFound
    }
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