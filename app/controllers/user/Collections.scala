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
import models.{Visibility, DObject, UserCollection}

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
            val objectIds = for(o <- collectionModel.objects) yield o.id.get
            DObject.update(("_id" $in objectIds), ($addToSet ("collections" -> iid)), false, true)
            SolrServer.indexSolrDocument(newCollection.toSolrDocument)
            SolrServer.commit()
            Some(collectionModel.copy(id = inserted))
        }
      case Some(id) =>
        val existingCollection = UserCollection.findOneByID(id)
        if (existingCollection == None) NotFound(&("user.collections.objectNotFound", id))
        val updatedUserCollection = existingCollection.get.copy(TS_update = new Date(), name = collectionModel.name, description = collectionModel.description, thumbnail_id = collectionModel.thumbnail, visibility = Visibility.get(collectionModel.visibility))
        try {
          // objects

          // FIXME here we should be updating the added/removed objects
          // to that end we should save the reverse links from object / mdr to UserCollection, with additional information (url, title, thumbnail, ...)

          SolrServer.indexSolrDocument(updatedUserCollection.toSolrDocument)
          UserCollection.update(MongoDBObject("_id" -> id), updatedUserCollection, false, false, WriteConcern.SAFE)
          SolrServer.commit()
          Some(collectionModel)
        } catch {
          case _ =>
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