package controllers.user

import controllers.DelvingController
import play.mvc.results.Result
import models.salatContext._
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import com.novus.salat.grater
import models._
import util.Constants._
import Link.LinkType._
import scala.collection.JavaConversions.asScalaMap
import java.lang.String
import com.mongodb.casbah.MongoCollection
import play.mvc.Before

/**
 * Controller to add simple, free-text labels to Things.
 * This actually creates links that hold as a value the label
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Links extends DelvingController {

  @Before(priority = 1) def checkUser(linkType: String, hubType: String, id: ObjectId): Result = {
    linkType match {
      case FREETEXT | PLACE =>
        if (connectedUser != params.get("user")) {
          return Forbidden(&("user.secured.noAccess"))
        }
      case PARTOF =>
        hubType match {
          case USERCOLLECTION =>
            if(UserCollection.count(MongoDBObject("_id" -> id, "userName" -> connectedUser)) == 0) {
              return Forbidden(&("user.secured.noAccess"))
            }
          case _ => BadRequest
        }
      case _ => return BadRequest
    }
    Continue
  }


  /**
   * Add a link between a hub entity and something else
   */
  def add(id: ObjectId, hubType: String, linkType: String): Result = {

    val label = params.get("label")
    if(label == null || label.isEmpty) BadRequest

    val filter = List("id", "linkType", "hubType", "page", "user")
    val filteredParams: Map[String, String] = params.allSimple().filterNot(e => filter.contains(e._1)).toMap

    // collection of the hub type we link against, passed in via the router
    val targetCollection: MongoCollection = ht(hubType) match {
      case Some(col) => col
      case None => return BadRequest
    }

    linkType match {

      case Link.LinkType.FREETEXT =>
        val toMongoCollection = targetCollection
        addLink(id, toMongoCollection, label, Link.create(
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
        val fromMongoCollection = targetCollection
        addLink(id, fromMongoCollection, label, Link.create(
          linkType = Link.LinkType.PLACE,
          userName = connectedUser,
          value = filteredParams,
          from = LinkReference(
            id = Some(id),
            hubType = Some(hubType)),
          to = LinkReference(refType = Some("place"), uri = Some("http://sws.geonames.org/%s/".format(filteredParams("geonameID"))))
        ))

      case Link.LinkType.PARTOF =>
        hubType match {
          case USERCOLLECTION =>

            // link a UserCollection to an MDR
            // URL is /{orgId}/object/{spec}/{recordId}/link/{id}
            // we store an EmbeddedLink in the MDR so that we can index it without additional lookup
            // for this, reconstruct where it is stored
            val orgId: String = params.get("orgId").toString
            val spec: String = params.get("spec").toString
            val recordId: String = params.get("recordId").toString
            val recordCollectionName = DataSet.getRecordsCollectionName(orgId, spec)
            val collection: MongoCollection = connection(recordCollectionName)

            // sanity check
            val ohBeOne = collection.findOne(MongoDBObject("localRecordKey" -> recordId))
            val mdr = ohBeOne match {
              case Some(one) => one
              case None => return NotFound("Record with identifier %s_%s_%s was not found, cannot link to it".format(orgId, spec, recordId))
            }
            addLink(mdr._id.get, collection, id.toString,  Link.create(
              linkType = Link.LinkType.PARTOF,
              userName = connectedUser,
              value = Map(USERCOLLECTION_ID -> id),
              from = LinkReference(
                uri = Some("http://%s/%s/object/%s/%s".format(getNode, orgId, spec, recordId)), // TODO need TW blessing
                refType = Some("institutionalObject") // TODO need TW blessing
              ),
              to = LinkReference(
                id = Some(id),
                hubType = Some(USERCOLLECTION)
              )
            ))
          case _ => BadRequest
        }


      case _ => BadRequest
    }
  }

  /**
  * Add a link involving a hub entity. There will be a denormalized copy of the link saved in the document pointed by (toId, toHubType)
  * TODO eventually add support to store also in another entity (from)
  */
  private def addLink(toId: ObjectId, mongoCollection: MongoCollection, label: String, createLink: => (Option[ObjectId], Link)): Result = {

    val created = createLink

    val lid = created._1 match {
      case Some(i) => i
      case None => return Error("Could not create link")
    }

    val embedded = EmbeddedLink(userName = connectedUser, linkType = created._2.linkType, link = lid, value = created._2.value)
    val serEmb = grater[EmbeddedLink].asDBObject(embedded)
    mongoCollection.update(MongoDBObject("_id" -> toId), $push ("links" -> serEmb))

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
      case OBJECT => Some(objectsCollection)
      case USERCOLLECTION => Some(userCollectionsCollection)
      case STORY => Some(userStoriesCollection)
      case USER => Some(userCollection)
      case _ => None
  }

}