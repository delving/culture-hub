package controllers.user

import controllers.DelvingController
import play.mvc.results.Result
import models.salatContext._
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import com.novus.salat.grater
import models._

/**
 * Controller to add simple, free-text labels to Things.
 * This actually creates links that hold as a value the label
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Labels extends DelvingController with UserSecured {

  def add(id: ObjectId, targetType: String): Result = {

    val targetCollection: MongoCollection = tc(targetType) match {
      case Some(col) => col
      case None => return Error(400, "Bad request")
    }

    val link: Option[ObjectId] = Link.create(
      linkType = "freeText",
      userName = connectedUser,
      value = LinkValue(label = params.get("label")),
      from = LinkReference(
        id = Some(connectedUserId),
        hubType = Some("User")),
      to = LinkReference(
        id = Some(id),
        hubType = Some(targetType)))

    val lid = link match {
      case Some(i) => i
      case None => return Error("Could not create label")
    }

    val embedded = EmbeddedLink(userName = connectedUser, link = lid, value = LinkValue(label = params.get("label")))
    val serEmb = grater[EmbeddedLink].asDBObject(embedded)
    targetCollection.update(MongoDBObject("_id" -> id), $push ("labels" -> serEmb))

    Json(Map("id" -> lid))
  }

  def remove(id: ObjectId, label: ObjectId, targetType: String): Result = {

    val targetCollection: MongoCollection = tc(targetType) match {
      case Some(col) => col
      case None => return Error(400, "Bad request")
    }

    targetCollection.update(MongoDBObject("_id" -> id), $pull ("labels" -> MongoDBObject("label" -> label)))
    labelsCollection.remove(MongoDBObject("_id" -> label))

    Ok
  }

  private def tc(targetType: String): Option[MongoCollection] = targetType match {
      case "DObject" => Some(objectsCollection)
      case "Collection" => Some(userCollectionsCollection)
      case "Story" => Some(userStoriesCollection)
      case "User" => Some(userCollection)
      case _ => None
  }

}