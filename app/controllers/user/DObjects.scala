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
import extensions.JJson._
import com.novus.salat.dao.SalatDAOUpdateError
import play.libs.Codec
import controllers._
import com.mongodb.WriteConcern
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import play.data.validation.Annotations._
import java.util.Date
import controllers.dos.FileUploadResponse
import extensions.JJson
import models._
import models.salatContext._
import util.Constants._
import play.mvc.Util

/**
 * Controller for manipulating user objects (creation, update, ...)
 * listing and display is done in the other controller that does not require authentication
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DObjects extends DelvingController with UserSecured {

  private def load(id: String): String = {
    val availableCollections = UserCollection.browseByUser(connectedUserId, connectedUserId).toList map { c => CollectionReference(c._id, c.name) }
    DObject.findByIdUnsecured(id) match {
        case None => JJson.generate(ObjectModel(availableCollections = availableCollections))
        case Some(anObject) => {
          val model = ObjectModel(
          Some(anObject._id),
          anObject.name,
          anObject.description,
          anObject.user_id,
          anObject.visibility.value,
          anObject.flattenLinksWithIds(Link.LinkType.PARTOF, USERCOLLECTION_ID).map(_._2),
          availableCollections,
          anObject.files map {f => FileUploadResponse(name = f.name, size = f.length, url = "/file/" + f.id, thumbnail_url = f.thumbnailUrl, delete_url = "/file/" + f.id, selected = Some(f.id) == anObject.thumbnail_file_id, id = f.id.toString)},
          anObject.thumbnail_file_id.toString)
          val generated: String = JJson.generate[ObjectModel](model)
          generated
        }
      }
  }

  def dobject(id: String): Result = {
    renderArgs += ("viewModel", classOf[ObjectModel])
    Template('id -> Option(id), 'uid -> Codec.UUID(), 'data -> load(id))
  }

  def objectSubmit(data: String, uid: String): Result = {
    val objectModel: ObjectModel = parse[ObjectModel](data)
    validate(objectModel).foreach { errors => return JsonBadRequest(objectModel.copy(errors = errors)) }

    val files = controllers.dos.FileUpload.getFilesForUID(uid)

    /** finds thumbnail candidate for an object, "activate" thumbnails (for easy lookup) and returns the OID of the thumbnail candidate image file **/
    def activateThumbnail(itemId: ObjectId, fileId: String): Option[ObjectId] = if(!fileId.isEmpty) {
      // TODO check if file can be thumbnailized
      val selectedFile = fileId match {
        case id if ObjectId.isValid(id) => Some(new ObjectId(id))
        case name if(files.exists(_.name == name)) => Some(files.filter(_.name == name).head.id)
        case first if files.length > 0 => Some(files.head.id)
        case _ => return None
      }
      controllers.dos.FileUpload.activateThumbnails(selectedFile.get, itemId); selectedFile
    } else {
      None
    }

    def addCollectionLinks(links: List[ObjectId], objectId: ObjectId) {
      links foreach {
        createCollectionLink(_, objectId)
      }
    }

    val persistedObject: Either[(String, Option[Throwable]), ObjectModel] = objectModel.id match {
      case None =>
        val newObject: DObject = DObject(
          TS_update = new Date(),
          name = objectModel.name,
          description = objectModel.description,
          user_id = connectedUserId,
          userName = connectedUser,
          visibility = Visibility.get(objectModel.visibility),
          thumbnail_id = None,
          files = files)
        
        val inserted: Option[ObjectId] = DObject.insert(newObject)

        inserted match {
          case Some(iid) => {
            // link collections
            addCollectionLinks(objectModel.collections, iid)

            // link files
            controllers.dos.FileUpload.markFilesAttached(uid, iid)

            // activate thumbnails
            val thumbnailId = activateThumbnail(iid, objectModel.selectedFile) match {
              case Some(thumb) => DObject.updateThumbnail(iid, thumb); Some(iid)
              case None => None
            }

            // index
            SolrServer.pushToSolr(newObject.copy(_id = iid, thumbnail_id = thumbnailId).toSolrDocument)
            Right(objectModel.copy(id = inserted))
          }
          case None => Left("Not saved", None)
        }
      case Some(id) =>
        val existingObject = DObject.findOneByID(id)
        if(existingObject == None) Error(&("user.dobjects.objectNotFound", id))
        val updatedObject = existingObject.get.copy(TS_update = new Date(), name = objectModel.name, description = objectModel.description, visibility = Visibility.get(objectModel.visibility), user_id = connectedUserId, files = existingObject.get.files ++ files)
        try {
          DObject.update(MongoDBObject("_id" -> id), updatedObject, false, false, WriteConcern.SAFE)

          // update collection links - we use the existing object, in its previous state
          val existingCollectionLinks = existingObject.get.flattenLinksWithIds(Link.LinkType.PARTOF, USERCOLLECTION_ID)
          val intersection = objectModel.collections.intersect(existingCollectionLinks.map(_._2))
          val removedLinks = existingCollectionLinks.filterNot(e => intersection.contains(e._2))
          val added = objectModel.collections.filterNot(intersection.contains(_))

          // remove removed links
          removedLinks.foreach {
            r => Link.removeById(r._1.link)
          }

          // add added
          addCollectionLinks(added, id)

          // link files
          controllers.dos.FileUpload.markFilesAttached(uid, id)

          // activate thumbnails
          val thumbnailId = activateThumbnail(id, objectModel.selectedFile) match {
            case Some(thumb) => DObject.updateThumbnail(id, thumb); Some(id)
            case None => None
          }

          // re-query that damn thing
          val updatedUpdatedObject = DObject.findOneByID(updatedObject._id).get

          // index
          SolrServer.indexSolrDocument(updatedUpdatedObject.toSolrDocument)
          SolrServer.commit()

          Right(objectModel)
        } catch {
          case e: SalatDAOUpdateError =>
            SolrServer.rollback()
            Left(e.getMessage, Some(e))
          case t: Throwable =>
            SolrServer.rollback()
            Left(t.getMessage, Some(t))
        }
    }

    persistedObject match {
      case Right(theObject) => {
        Json(theObject)
      }
      case Left((message, exception)) =>
        logError(exception.getOrElse(new Exception(message)), message)
        Error(&("user.dobjects.saveError", objectModel.name))
    }
  }

  def remove(id: ObjectId) = {
    if(DObject.owns(connectedUserId, id)) {
      DObject.delete(id)
      SolrServer.deleteFromSolrById(id)
      SolrServer.commit()
    } else {
      Forbidden("Big brother is watching you")
    }
  }

  @Util def createCollectionLink(collectionId: ObjectId, objectId: ObjectId) = {
    Link.create(
      linkType = Link.LinkType.PARTOF,
      userName = connectedUser,
      value = Map(USERCOLLECTION_ID -> collectionId),
      from = LinkReference(
        id = Some(objectId),
        hubType = Some(OBJECT)
      ),
      to = LinkReference(
        id = Some(collectionId),
        hubType = Some(USERCOLLECTION)
      ),
      embedFrom = Some(EmbeddedLinkWriter(
        collection = objectsCollection,
        id = Some(objectId)
      )),
      embedTo = Some(EmbeddedLinkWriter(
        value = Some(Map(OBJECT_ID -> objectId)),
        collection = userCollectionsCollection,
        id = Some(collectionId)
      ))
    )
  }

}

// ~~~ view models

// TODO replace owner objectId with userName
case class ObjectModel(id: Option[ObjectId] = None,
                       @Required name: String = "",
                       @Required description: String = "",
                       owner: ObjectId = new ObjectId(),
                       visibility: Int = Visibility.PRIVATE.value,
                       collections: List[ObjectId] = List.empty[ObjectId],
                       availableCollections: List[CollectionReference] = List.empty[CollectionReference],
                       files: Seq[FileUploadResponse] = Seq.empty[FileUploadResponse],
                       selectedFile: String = "",
                       errors: Map[String, String] = Map.empty[String, String]) extends ViewModel