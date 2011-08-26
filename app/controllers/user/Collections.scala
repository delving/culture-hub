package controllers.user

import play.templates.Html
import views.User.Collection._
import play.mvc.results.Result
import controllers.{CollectionModel, Secure, UserAuthentication, DelvingController}
import org.bson.types.ObjectId
import com.mongodb.WriteConcern
import com.novus.salat.dao.SalatDAOUpdateError
import extensions.CHJson._
import com.mongodb.casbah.commons.MongoDBObject
import org.scala_tools.time.Imports._
import models.{UserAction, AccessRight, UserCollection}

/**
 * Manipulation of user collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController with UserAuthentication with Secure {

  def collectionUpdate(id: String): Html = html.add(Option(id))

  def collectionSubmit(data: String): Result = {
    val CollectionModel: CollectionModel = parse[CollectionModel](data)
    val persistedUserCollection = CollectionModel.id match {
      case None =>
        val inserted: Option[ObjectId] = UserCollection.insert(
          UserCollection(TS_update = DateTime.now,
            name = CollectionModel.name,
            node = getNode,
            user = connectedUserId,
            description = CollectionModel.description))
//            access = AccessRight(users = Map(getUserReference.id -> UserAction(user = getUserReference, read = Some(true), update = Some(true), delete = Some(true), owner = Some(true))))))
        if (inserted != None) Some(CollectionModel.copy(id = inserted)) else None
      case Some(id) =>
        val existingObject = UserCollection.findOneByID(id)
        if (existingObject == None) Error("Object with id %s not found".format(id))
        val updatedUserCollection = existingObject.get.copy(TS_update = DateTime.now, name = CollectionModel.name, description = CollectionModel.description)
        try {
          UserCollection.update(MongoDBObject("_id" -> id), updatedUserCollection, false, false, new WriteConcern())
          Some(CollectionModel)
        } catch {
          case e: SalatDAOUpdateError => None
          case _ => None
        }
    }

    persistedUserCollection match {
      case Some(theObject) => Json(theObject)
      case None => Error("Error saving object")
    }
  }

}