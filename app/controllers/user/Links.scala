package controllers.user

import controllers.DelvingController
import play.mvc.results.Result
import models.salatContext._
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import com.novus.salat.grater
import models._
import scala.collection.JavaConversions.asScalaMap

/**
 * Controller to add simple, free-text labels to Things.
 * This actually creates links that hold as a value the label
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Links extends DelvingController with UserSecured {

  def add(id: ObjectId, hubType: String, linkType: String): Result = {

    val label = params.get("label")
    if(label == null || label.isEmpty) BadRequest

    val filter = List("id", "linkType", "hubType", "page", "user")
    val filteredParams: Map[String, String] = params.allSimple().filterNot(e => filter.contains(e._1)).toMap

    linkType match {
      case Link.LinkType.FREETEXT =>
        addLink(id, hubType, label, Link.create(
          linkType = Link.LinkType.FREETEXT,
          userName = connectedUser,
          value = Map("label" -> label),
          from = LinkReference(
            id = Some(connectedUserId),
            hubType = Some("user")),
          to = LinkReference(
            id = Some(id),
            hubType = Some(hubType)))
        )
      case Link.LinkType.PLACE =>

        addLink(id, hubType, label, Link.create(
          linkType = Link.LinkType.PLACE,
          userName = connectedUser,
          value = filteredParams,
          from = LinkReference(
            id = Some(id),
            hubType = Some("object")),
          to = LinkReference(refType = Some("place"), uri = Some("http://sws.geonames.org/%s/".format(filteredParams("geonameId"))))
        ))
      case _ => BadRequest
    }
  }

  private def addLink(id: ObjectId, targetType: String, label: String, createLink: => (Option[ObjectId], Link)): Result = {

    val targetCollection: MongoCollection = ht(targetType) match {
      case Some(col) => col
      case None => return BadRequest
    }

    val created = createLink

    val lid = created._1 match {
      case Some(i) => i
      case None => return Error("Could not create link")
    }

    val embedded = EmbeddedLink(userName = connectedUser, linkType = created._2.linkType, link = lid, value = created._2.value)
    val serEmb = grater[EmbeddedLink].asDBObject(embedded)
    targetCollection.update(MongoDBObject("_id" -> id), $push ("links" -> serEmb))

    Json(Map("id" -> lid))
  }

  /**
  * Remove a link by ID
  */
  def remove(id: ObjectId, link: ObjectId, hubType: String): Result = {

    val targetCollection: MongoCollection = ht(hubType) match {
      case Some(col) => col
      case None => return Error(400, "Bad request")
    }

    val wr = targetCollection.update(MongoDBObject("_id" -> id), $pull("links" -> MongoDBObject("link" -> link)), false, false, WriteConcern.Safe)
    // TODO fixme...
    if(wr.getN == 0 || !wr.getLastError.ok()) {
      logError("Could not delete label ")
      return Error("Could not delete label")
    }
    linksCollection.remove(MongoDBObject("_id" -> link))
    Ok
  }

  private def ht(hubType: String): Option[MongoCollection] = hubType match {
      case "object" => Some(objectsCollection)
      case "collection" => Some(userCollectionsCollection)
      case "story" => Some(userStoriesCollection)
      case "user" => Some(userCollection)
      case _ => None
  }

}