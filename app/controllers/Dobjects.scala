package controllers

import play.mvc.results.Result
import com.mongodb.casbah.commons.MongoDBObject
import models.{DObject, UserCollection}
import org.bson.types.ObjectId

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object DObjects extends DelvingController {

  import views.Dobject._

  def list(user: Option[String], query: String, page: Int = 1): AnyRef = {

    // TODO access rights
    val objectsPage = user match {
      case Some(u) => DObject.findByUser(browsedUserId).page(page)
      case None => DObject.findAll().page(page)
    }

    request.format match {
      case "html" => html.list(objects = objectsPage._1 map { o => ShortObject(o._id, o.name, o.description.getOrElse(""), "", o.userName) }, page = page, count = objectsPage._2)
      case "json" => Json(objectsPage._1)
    }
  }

  def view(user: String, id: String): AnyRef = {
    DObject.findById(id) match {
        case None => NotFound
        case Some(anObject) => html.dobject(dobject = anObject)
      }
  }

  def load(id: String): Result = {
    DObject.findById(id) match {
        case None => Json(ObjectModel())
        case Some(anObject) => {
          val collections = ObjectModel.objectIdListToCollections(anObject.collections)
          Json(ObjectModel(Some(anObject._id), anObject.name, anObject.description, anObject.user_id, collections, anObject.files map {f => FileUploadResponse(f.name, f.length)}))
        }
      }
  }
}

// ~~~ list page models

case class ShortObject(id: ObjectId, name: String, shortDescription: String, thumbnailUrl: String, userName: String)


// ~~~ view models

case class ObjectModel(id: Option[ObjectId] = None,
                       name: String = "",
                       description: Option[String] = Some(""),
                       owner: ObjectId = new ObjectId(),
                       collections: List[Collection] = List.empty[Collection],
                       files: Seq[FileUploadResponse] = Seq.empty[FileUploadResponse]) {

  def getCollections: List[ObjectId] = for(collection <- collections) yield new ObjectId(collection.id)
}

object ObjectModel {

  def objectIdListToCollections(collectionIds: List[ObjectId]) = {
    (for (userCollection: UserCollection <- UserCollection.find(MongoDBObject("_id" -> MongoDBObject("$in" -> collectionIds))))
    yield Collection(userCollection._id.toString, userCollection.name)).toList
  }

}

case class Collection(id: String, name: String)
