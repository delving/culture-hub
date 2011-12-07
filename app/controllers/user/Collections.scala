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
import models.{Link, Visibility, DObject, UserCollection}
import util.Constants._
import collection.immutable.List

/**
 * Manipulation of user collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController with UserSecured {

  private def load(id: String): String = {
    val allObjects = (DObject.browseByUser(browsedUserId, connectedUserId).map {o => ObjectModel(Some(o._id), o.name, o.description, o.user_id)}).toList

    UserCollection.findById(id, connectedUserId) match {
      case None => JJson.generate(CollectionViewModel(allObjects = allObjects, availableObjects = allObjects))
      case Some(col) => {
        val objects = DObject.findAllWithCollection(col._id).toList map { obj => ObjectModel(Some(obj._id), obj.name, obj.description, obj.user_id)}
        JJson.generate(CollectionViewModel(
          id = Some(col._id),
          name = col.name,
          description = col.description,
          allObjects = allObjects,
          objects = objects,
          availableObjects = (allObjects filterNot (objects contains)),
          thumbnail = col.thumbnail_id, visibility = col.visibility.value))
      }
    }
  }


  def collection(id: String): Result = {
    renderArgs += ("viewModel", classOf[CollectionViewModel])
    Template('id -> Option(id), 'data -> load(id))
  }

  def collectionSubmit(data: String): Result = {

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
            thumbnail_id = collectionModel.thumbnail)
        val inserted: Option[ObjectId] = UserCollection.insert(newCollection)
        inserted match {
          case None => None
          case Some(iid) =>
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
        val existingCollection = UserCollection.findOneByID(id)
        if (existingCollection == None) NotFound(&("user.collections.objectNotFound", id))
        val updatedUserCollection = existingCollection.get.copy(TS_update = new Date(), name = collectionModel.name, description = collectionModel.description, thumbnail_id = collectionModel.thumbnail, visibility = Visibility.get(collectionModel.visibility))
        try {
          UserCollection.update(MongoDBObject("_id" -> id), updatedUserCollection, false, false, WriteConcern.SAFE)

          // update link to objects
          val linkedObjects = existingCollection.get.flattenLinksWithIds(Link.LinkType.PARTOF, OBJECT_ID)
          val updatedObjects: List[ObjectId] = collectionModel.objects.map(_.id.get)
          val intersection = updatedObjects.intersect(linkedObjects.map(_._2))
          val removedLinks = linkedObjects.filterNot(e => intersection.contains(e._2))
          val added = updatedObjects.filterNot(intersection.contains(_))

          removedLinks.foreach {
            r => Link.removeById(r._1.link)
          }

          added foreach { o =>
            user.DObjects.createCollectionLink(id, o)
          }

          val affectedObjectIds = removedLinks.map(_._2) ++ added
          affectedObjectIds foreach { affected =>
            val obj = DObject.findOneByID(affected).get
            SolrServer.indexSolrDocument(obj.toSolrDocument)
          }

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
      case Some(theCollection) => {
        val objectIds = for(o <- collectionModel.objects) yield o.id.get

        // FIXME remove collections that were removed...
        DObject.update(("_id" $in objectIds), ($addToSet ("collections" -> theCollection.id.get)), false, true)

        Json(theCollection)
      }
      case None => Error(&("user.collections.saveError", collectionModel.name))
    }

  }

  def remove(id: ObjectId) = {
    if(UserCollection.owns(connectedUserId, id)) {
      val objects = DObject.findForCollection(id)
      UserCollection.setObjects(id, objects)
      DObject.unlinkCollection(id)
      UserCollection.delete(id)
      SolrServer.deleteFromSolrById(id)
      SolrServer.commit()
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
                              objects: List[ObjectModel] = List.empty[ObjectModel],
                              allObjects: List[ObjectModel] = List.empty[ObjectModel],
                              availableObjects: List[ObjectModel] = List.empty[ObjectModel],
                              visibility: Int = Visibility.PRIVATE.value,
                              thumbnail: String = "",
                              errors: Map[String, String] = Map.empty[String, String]) extends ViewModel