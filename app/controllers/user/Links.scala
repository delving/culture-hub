/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.user

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
import components.Indexing
import controllers.{SolrServer, DelvingController}
import collection.immutable.Map

/**
 * Controller to add simple, free-text labels to Things.
 * This actually creates links that hold as a value the label
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Links extends DelvingController {

  @Before(priority = 1) def checkUser(linkType: String, fromType: String, toType: String, toId: ObjectId): Result = {
    linkType match {
      case FREETEXT | PLACE =>
        if (connectedUser != params.get("user")) {
          return Forbidden(&("user.secured.noAccess"))
        }
      case PARTOF =>
        toType match {
          case USERCOLLECTION =>
            if(UserCollection.count(MongoDBObject("_id" -> toId, "userName" -> connectedUser)) == 0) {
              return Forbidden(&("user.secured.noAccess"))
            }
          case _ => BadRequest("unmached fromType " + fromType)
        }
      case _ => return BadRequest("unmatched linkType " + linkType)
    }
    Continue
  }


  /**
   * Add a link between a hub entity and something else
   */
  def add(fromId: ObjectId, fromType: String, linkType: String, toType: String, toId: String): Result = {

    val label = params.get("label")

    val filter = List("fromId", "toId", "linkType", "fromType", "toType", "page", "user", "body", "_id")
    val filteredParams: Map[String, String] = params.allSimple().filterNot(e => filter.contains(e._1)).toMap

    // collection of the hub type we link against, passed in via the router
    val targetCollection: MongoCollection = ht(fromType) match {
      case Some(col) => col
      case None => return BadRequest("unmatched fromType " + fromType)
    }

    val created = linkType match {

      case Link.LinkType.FREETEXT =>
        if(label == null || label.isEmpty) return BadRequest("empty label")
        val toObjectId = toId match {
          case oid if ObjectId.isValid(oid) => new ObjectId(oid)
          case _ => return BadRequest("bad oid")
        }

        Link.create(
          linkType = Link.LinkType.FREETEXT,
          userName = connectedUser,
          value = Map("label" -> label),
          from = LinkReference(
            uri = Some(Link.buildUri(USER, connectedUser, request.host)),
            id = Some(connectedUserId),
            hubType = Some(USER)),
          to = LinkReference(
            uri = Some(Link.buildUri(OBJECT, toObjectId, request.host)),
            id = Some(toObjectId),
            hubType = Some(toType)),
          embedTo = Some(EmbeddedLinkWriter(
            collection = objectsCollection,
            id = Some(toObjectId)))
        )

      case Link.LinkType.PLACE =>
        if(label == null || label.isEmpty) return BadRequest("empty label")
        val fromMongoCollection = targetCollection
        Link.create(
          linkType = Link.LinkType.PLACE,
          userName = connectedUser,
          value = filteredParams,
          from = LinkReference(
            uri = Some(Link.buildUri(fromType, fromId, request.host)),
            id = Some(fromId),
            hubType = Some(fromType)),
          to = LinkReference(
            refType = Some("place"),
            uri = Some("http://sws.geonames.org/%s/".format(toId))),
          embedFrom = Some(EmbeddedLinkWriter(
            collection = fromMongoCollection,
            id = Some(fromId)
          ))
        )

      case Link.LinkType.PARTOF =>
        toType match {
          case USERCOLLECTION =>
            val collectionId: ObjectId = toId match {
              case cid if ObjectId.isValid(cid) => new ObjectId(cid)
              case _ => return BadRequest("Invalid collectionId " + toId)
            }
            UserCollection.findByIdSecured(collectionId, connectedUser) match {
              case Some(col) => col
              case None => return NotFound("UserCollection with ID %s not found".format(collectionId))
            }

            fromType match {
              case MDR =>
                // link a UserCollection to an MDR
                // URL is /{orgId}/object/{spec}/{recordId}/link/{toType}/{toId}
                // we store an EmbeddedLink in the MDR so that we can index it without additional lookup
                // for this, reconstruct where it is stored
                val (collection, orgId, spec, recordId) = mdrInfo

                // sanity check
                val ohBeOne = collection.findOne(MongoDBObject("localRecordKey" -> recordId))
                val mdr = ohBeOne match {
                  case Some(one) => one
                  case None => return NotFound("Record with identifier %s_%s_%s was not found".format(orgId, spec, recordId))
                }
                val hubId = "%s_%s_%s".format(orgId, spec, recordId)
                val res = Link.create(
                  linkType = Link.LinkType.PARTOF,
                  userName = connectedUser,
                  value = Map(USERCOLLECTION_ID -> toId),
                  from = LinkReference(
                    uri = Some(Link.buildUri(MDR, hubId, request.host)),
                    refType = Some("institutionalObject"), // TODO need TW blessing
                    hubType = Some(MDR),
                    hubCollection = Some(collection.getName()),
                    hubAlternativeId = Some(hubId)
                  ),
                  to = LinkReference(
                    uri = Some(Link.buildUri(USERCOLLECTION, collectionId, request.host)),
                    id = Some(collectionId),
                    hubType = Some(USERCOLLECTION)
                  ),
                  embedFrom = Some(EmbeddedLinkWriter(
                    collection = collection,
                    id = mdr._id
                  )),
                  embedTo = Some(EmbeddedLinkWriter(
                    value = Some(Map(
                      MDR_HUB_ID -> mdr.get(MDR_HUB_ID).toString,
                      MDR_LOCAL_ID -> mdr.get(MDR_LOCAL_ID).toString,
                      MDR_HUBCOLLECTION -> collection.getName())
                    ),
                    collection = userCollectionsCollection,
                    id = Some(collectionId))
                  ))

                // re-index the MDR
                collection.findOne(MongoDBObject("localRecordKey" -> recordId)) match {
                  case Some(one) =>
                    val mdr = grater[MetadataRecord].asObject(one)
                    Indexing.indexOneInSolr(orgId, spec, mdr)
                  case None => // huh?
                    warning("MDR %s_%s_%s does not exist!", orgId, spec, recordId)
                }
                
                // re-index the UserCollection
                SolrServer.pushToSolr(UserCollection.findOneByID(collectionId).get.toSolrDocument)

                res

              case OBJECT =>
                val res = DObjects.createCollectionLink(new ObjectId(toId), fromId, request.host)

                // re-index the object
                DObject.findOneByID(fromId) match {
                  case Some(obj) =>
                    SolrServer.indexSolrDocument(obj.toSolrDocument)
                    SolrServer.commit()
                  case None =>
                    warning("Object with ID %s does not exist!".format(fromId.toString))
                }
                res

              case _ => return BadRequest("unmatched fromType with toType collection " + fromType)
            }

          case _ => return BadRequest("unmatched fromType " + fromType)
        }

      case _ => return BadRequest("unmatched LinkType " + linkType)
    }

    val lid: ObjectId = created._1 match {
      case Some(linkId) => linkId
      case None => return Error("Could not create link")
    }

    Json(Map("id" -> lid))

  }

  /**
  * Remove a link by ID
  */
  def removeById(id: ObjectId, link: ObjectId, fromType: String): Result = {
    Link.removeById(link)
    Ok
  }

  def remove(id: ObjectId, linkType: String, toType: String, toId: ObjectId): Result = {
    val (collection, orgId, spec, recordId) = mdrInfo
    Link.findOne(MongoDBObject("from.uri" -> Link.buildUri(MDR, "%s_%s_%s".format(orgId, spec, recordId), request.host), "linkType" -> linkType, "to.id" -> toId)) match {
      case Some(l) => Link.removeLink(l)
      case None => // nope
    }
    Ok
  }

  private def mdrInfo = {
    val orgId: String = params.get("orgId").toString
    val spec: String = params.get("spec").toString
    val recordId: String = params.get("recordId").toString
    val recordCollectionName = DataSet.getRecordsCollectionName(orgId, spec)
    val collection: MongoCollection = connection(recordCollectionName)

    (collection, orgId, spec, recordId)

  }

  def ht(fromType: String): Option[MongoCollection] = fromType match {
      case OBJECT => Some(objectsCollection)
      case USERCOLLECTION => Some(userCollectionsCollection)
      case STORY => Some(userStoriesCollection)
      case USER => Some(userCollection)
      case MDR =>
        val (collection, orgId, spec, recordId) = mdrInfo
        Some(collection)
      case _ => None
  }

}