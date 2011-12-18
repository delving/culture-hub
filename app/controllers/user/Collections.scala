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
import org.bson.types.ObjectId
import com.mongodb.WriteConcern
import extensions.JJson
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import controllers._
import play.data.validation.Annotations._
import java.util.Date
import util.Constants._
import collection.immutable.List
import models.salatContext._
import com.novus.salat.grater
import models._
import components.Indexing

/**
 * Manipulation of user collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController with UserSecured {

  private def load(id: String): String = {
    val allObjects: List[ShortObjectModel] = DObject.browseByUser(browsedUserId, connectedUserId).toList

    UserCollection.findById(id, connectedUserId) match {
      case None =>
        JJson.generate[CollectionViewModel](CollectionViewModel(allObjects = allObjects, availableObjects = allObjects))
      case Some(col) => {
        // retrieve objects of the collections via the inbound links. This is not very efficient.
        val linkedObjectLinks = col.links.filter(_.linkType == Link.LinkType.PARTOF).map(_.link)
        val links = Link.find("_id" $in linkedObjectLinks).toList
        val userObjectIds = links.filter(_.from.hubType == Some(OBJECT)).map(_.from.id.get)
        val mdrIds = links.filter(_.from.hubType == Some(MDR)).map(l => (l.from.hubCollection.get, l.from.hubAlternativeId.get))

        val userObjects: List[ShortObjectModel] = DObject.find("_id" $in userObjectIds).toList

        val mdrs = mdrIds.groupBy(_._1).map(m => connection(m._1).find(MDR_HUB_ID $in m._2.map(_._2)).toList).flatten.map(grater[MetadataRecord].asObject(_))
        val convertedMdrs = mdrs.flatMap(mdr =>
          // we assume that the first mapping we find will do
          if(!mdr.mappedMetadata.isEmpty) {
            val Array(orgId, spec, localRecordKey) = mdr.hubId.split("_")
            DataSet.findBySpecAndOrgId(spec, orgId) match {
              case Some(ds) =>
                val record = mdr.getAccessor(ds.getIndexingMappingPrefix.getOrElse(""))
                Some(ShortObjectModel(id = record.getHubId, url = record.getUri, thumbnail = record.getThumbnailUri(80), title = record.getTitle, hubType = MDR))
              case None =>
                warning("Could not find DataSet for " + mdr.hubId)
                None // huh?!?
            }
          } else {
            None
          })

        val objects = userObjects ++ convertedMdrs
        JJson.generate[CollectionViewModel](CollectionViewModel(
          id = Some(col._id),
          name = if(col.getBookmarksCollection) &("thing.bookmarksCollection") else col.name,
          description = if(col.getBookmarksCollection) &("thing.bookmarksCollectionDescription") else col.description,
          allObjects = allObjects,
          objects = objects,
          availableObjects = (allObjects filterNot (objects contains)),
          thumbnail = if(col.thumbnail_id != None) col.thumbnail_id.get else {
            col.links.find(_.linkType == Link.LinkType.THUMBNAIL).headOption match {
              case Some(e) => e.value(MDR_HUB_ID)
              case None => ""
            }
          },
          visibility = col.visibility.value,
          isBookmarksCollection = col.getBookmarksCollection))
      }
    }
  }


  def collection(id: String): Result = {
    renderArgs += ("viewModel", classOf[CollectionViewModel])
    Template('id -> Option(id), 'data -> load(id))
  }

  def collectionSubmit(data: String): Result = {
    
    def getThumbnailId(collectionId: ObjectId, thumbnailId: String, objects: List[ShortObjectModel]): Either[String, String] = thumbnailId match {
      case oid if ObjectId.isValid(oid) => Right(new ObjectId(oid))
      case hubId =>
        val thumbnailUrl = objects.find(_.id == hubId).get.thumbnail
        Left(thumbnailUrl)
    }

    def setThumbnaiL(collectionId: ObjectId, thumbnailId: String, objects: List[ShortObjectModel], thumbnailLink: Option[ObjectId]) {
      // remove existing thumbnail link, if any
      thumbnailLink.foreach(Link.removeById(_))
      getThumbnailId(collectionId, thumbnailId, objects) match {
        case Right(oid) =>
          UserCollection.update(MongoDBObject("_id" -> collectionId), $set ("thumbnail_id" -> oid) ++ $unset("thumbnail_url"))
        case Left(url) =>
          UserCollection.createThumbnailLink(collectionId, thumbnailId, connectedUser)
          UserCollection.update(MongoDBObject("_id" -> collectionId), $set("thumbnail_url" -> url) ++ $unset("thumbnail_id"))
      }
    }

    val collectionModel: CollectionViewModel = JJson.parse[CollectionViewModel](data)
    validate(collectionModel).foreach { errors => return JsonBadRequest(collectionModel.copy(errors = errors)) }

    val persistedUserCollection: Option[CollectionViewModel] = collectionModel.id match {
      case None =>
        val newCollection = UserCollection(TS_update = new Date(),
            name = collectionModel.name,
            user_id = connectedUserId,
            userName = connectedUser,
            description = collectionModel.description,
            visibility = Visibility.get(collectionModel.visibility),
            thumbnail_id = None,
            thumbnail_url = None)
        val inserted: Option[ObjectId] = UserCollection.insert(newCollection)
        inserted match {
          case None => None
          case Some(iid) =>
            // set the thumbnail
            setThumbnaiL(iid, collectionModel.thumbnail, collectionModel.objects, None)

            // TODO this has to be replaced by a mechanism that does atomic adds / deletes in the view, not all at once
            for(o <- collectionModel.objects) {
              user.DObjects.createCollectionLink(iid, o.id.get)
              val obj = DObject.findOneByID(o.id.get).get
              SolrServer.indexSolrDocument(obj.toSolrDocument)
            }
            SolrServer.indexSolrDocument(newCollection.toSolrDocument)
            SolrServer.commit()
            Some(collectionModel.copy(id = inserted))
        }
      case Some(id) =>
        val updatedUserCollection = UserCollection.findOneByID(id) match {
          case None => return NotFound(&("user.collections.objectNotFound", id))
          case Some(existingCollection) =>
            existingCollection.getBookmarksCollection match {
              case false => existingCollection.copy(
                TS_update = new Date(),
                name = collectionModel.name,
                description = collectionModel.description,
                visibility = Visibility.get(collectionModel.visibility))
              case true => existingCollection.copy(TS_update = new Date())
          }
        }
        try {
          UserCollection.update(MongoDBObject("_id" -> id), updatedUserCollection, false, false, WriteConcern.SAFE)

          // update the thumbnail
          setThumbnaiL(id, collectionModel.thumbnail, collectionModel.objects, updatedUserCollection.links.filter(_.linkType == Link.LinkType.THUMBNAIL).map(_.link).headOption)

          val existingCollection = UserCollection.findOneByID(id)

          // update link to objects
          val linkedObjects = existingCollection.get.flattenLinksWithIds(Link.LinkType.PARTOF, OBJECT_ID)
          val updatedObjects: List[ObjectId] = collectionModel.objects.filter(_.hubType == OBJECT).map(o => new ObjectId(o.id))
          val intersection = updatedObjects.intersect(linkedObjects.map(_._2))
          val removedObjectLinks = linkedObjects.filterNot(e => intersection.contains(e._2))
          val added = updatedObjects.filterNot(intersection.contains(_))

          val linkedMdrs: List[(EmbeddedLink, String)] = existingCollection.get.links.filter(l => l.linkType == Link.LinkType.PARTOF && l.value.contains(HUB_ID)).map(e => (e, e.value(HUB_ID)))
          val updatedMdrs = collectionModel.objects.filter(_.hubType == MDR).map(_.id)
          val removedMdrs = linkedMdrs.filterNot(l => updatedMdrs.contains(l._2))

          // remove removed objects and MDRs
          (removedObjectLinks ++ removedMdrs).map(_._1.link).foreach {
            r => Link.removeById(r)
          }

          added foreach { o =>
            user.DObjects.createCollectionLink(id, o)
          }

          // synchronize SOLR index

          // removed user objects
          val affectedObjectIds = removedObjectLinks.map(_._2) ++ added
          affectedObjectIds foreach { affected =>
            val obj = DObject.findOneByID(affected).get
            SolrServer.indexSolrDocument(obj.toSolrDocument)
          }

          // removed MDRs
          for((embeddedLink, hubId: String) <- removedMdrs) {
            val hubCollection = embeddedLink.value(MDR_HUBCOLLECTION)
            connection(hubCollection).findOne(MongoDBObject(MDR_HUB_ID -> hubId)) match {
              case Some(dbo) =>
                val mdr = grater[MetadataRecord].asObject(dbo)
                val Array(orgId, spec, localRecordKey) = hubId.split("_")
                Indexing.indexOneInSolr(orgId, spec, mdr)
              case None =>
                // meh?
                warning("While updating UserCollection %s: could not find MDR with hubId %s, removed the document from SOLR", existingCollection.get._id, hubId)
                SolrServer.deleteFromSolrByQuery("%s:%s".format(HUB_ID, hubId))
            }
          }

          // user collection
          SolrServer.indexSolrDocument(updatedUserCollection.toSolrDocument)
          SolrServer.commit()
          Some(collectionModel)
        } catch {
          case t =>
            logError(t, "Could not save collection " + id)
            SolrServer.rollback()
            None
        }
    }

    persistedUserCollection match {
      case Some(theCollection) => Json(theCollection)
      case None => Error(&("user.collections.saveError", collectionModel.name))
    }

  }

  def remove(id: ObjectId): Result = {
    if(UserCollection.owns(connectedUserId, id)) {
      UserCollection.findOneByID(id) match {
        case Some(col) =>
          if(col.getBookmarksCollection) return Error("Cannot delete bookmarks collection!")
        case None =>
      }
      val objects = DObject.findForCollection(id)
      UserCollection.setObjects(id, objects)
      DObject.unlinkCollection(id)
      UserCollection.delete(id)
      SolrServer.deleteFromSolrById(id)
      Ok
    } else {
      Forbidden("Big brother is watching you")
    }
  }

  // TODO move someplace generic
  implicit def stringToObjectIdOption(id: String): Option[ObjectId] = if(!ObjectId.isValid(id)) None else Some(new ObjectId(id))
  implicit def objectIdOptionToString(id: Option[ObjectId]): String = id match {
    case None => ""
    case Some(oid) => oid.toString
  }
}

case class CollectionViewModel(id: Option[ObjectId] = None,
                              @Required name: String = "",
                              @Required description: String = "",
                              objects: List[ShortObjectModel] = List.empty[ShortObjectModel],
                              allObjects: List[ShortObjectModel] = List.empty[ShortObjectModel],
                              availableObjects: List[ShortObjectModel] = List.empty[ShortObjectModel],
                              visibility: Int = Visibility.PRIVATE.value,
                              thumbnail: String = "",
                              isBookmarksCollection: Boolean = false,
                              errors: Map[String, String] = Map.empty[String, String]) extends ViewModel