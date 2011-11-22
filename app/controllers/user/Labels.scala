package controllers.user

import controllers.DelvingController
import play.mvc.results.Result
import models.salatContext._
import models.ctx
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import models.{LabelReference, EmbeddedLabel, Label}
import com.novus.salat.grater

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Labels extends DelvingController with UserSecured {

  def create(labelType: String, value: String): Result = {

    val insert = Label.create(labelType, connectedUser, value, Option(params.get("geonameId")))
    val id = insert match {
      case Some(i) => i
      case None => return Error("Could not create label")
    }

    Json(Map("id" -> id))
  }

  def add(id: ObjectId, label: ObjectId, targetType: String): Result = {

    val targetCollection: MongoCollection = tc(targetType) match {
      case Some(col) => col
      case None => return Error(400, "Bad request")
    }

    val embedded = EmbeddedLabel(userName = connectedUser, label = label)
    val serEmb = grater[EmbeddedLabel].asDBObject(embedded)
    targetCollection.update(MongoDBObject("_id" -> id), $addToSet ("labels" -> serEmb))

    val reference = LabelReference(userName = connectedUser, id = id, targetType = targetType)
    val serRef = grater[LabelReference].asDBObject(reference)
    labelsCollection.update(MongoDBObject("_id" -> label), $addToSet("references" -> serRef))

    Ok
  }

  def remove(id: ObjectId, label: ObjectId, targetType: String): Result = {

    val targetCollection: MongoCollection = tc(targetType) match {
      case Some(col) => col
      case None => return Error(400, "Bad request")
    }

    targetCollection.update(MongoDBObject("_id" -> id), $pull ("labels" -> MongoDBObject("label" -> label)))
    labelsCollection.update(MongoDBObject("_id" -> id), $pull ("references" -> MongoDBObject("id" -> id)))

    Ok
  }

  private def tc(targetType: String): Option[MongoCollection] = targetType match {
      case "DObject" => Some(objectsCollection)
      case "Collection" => Some(userCollectionsCollection)
      case "Story" => Some(userStoriesCollection)
      case _ => None
  }

}