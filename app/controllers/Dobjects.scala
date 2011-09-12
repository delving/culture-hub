package controllers

import play.mvc.results.Result
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import models.{Label, DObject, UserCollection}
import org.joda.time.DateTime

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
      case None => DObject.findAll.page(page)
    }

    request.format match {
      case "html" => html.list(objects = objectsPage._1, page = page, count = objectsPage._2)
      case "json" => Json(objectsPage._1)
    }
  }

  def view(user: String, id: String): AnyRef = {
    DObject.findById(id) match {
        case None => NotFound
        case Some(anObject) => {
          val labels: List[ShortLabel] = anObject.labels
          html.dobject(dobject = anObject, labels = labels)
        }
      }
  }

  def load(id: String): Result = {
    DObject.findById(id) match {
        case None => Json(ObjectModel())
        case Some(anObject) => {
          val collections = ObjectModel.objectIdListToCollections(anObject.collections)
          Json(ObjectModel(Some(anObject._id), anObject.name, anObject.description, anObject.user_id, collections, (Label.findAllWithIds(anObject.labels) map {l => ShortLabel(l.labelType, l.value) }).toList, anObject.files map {f => FileUploadResponse(f.name, f.length)}))
        }
      }
  }
}

// ~~~ list page models

case class ShortObject(id: ObjectId, TS_update: DateTime, name: String, shortDescription: String, userName: String)


// ~~~ view models

case class ObjectModel(id: Option[ObjectId] = None,
                       name: String = "",
                       description: Option[String] = Some(""),
                       owner: ObjectId = new ObjectId(),
                       collections: List[Collection] = List.empty[Collection],
                       labels: List[ShortLabel] = List.empty[ShortLabel],
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