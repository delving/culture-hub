package controllers.user

import play.templates.Html
import views.User.Object._
import play.mvc.results.Result
import controllers.{ObjectModel, Secure, UserAuthentication, DelvingController}
import extensions.CHJson._
import models.Object
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.WriteConcern
import com.novus.salat.dao.SalatDAOUpdateError
import org.scala_tools.time.Imports._

/**
 * Controller for manipulating user objects (creation, update, ...)
 * listing and display is done in the other controller that does not require authentication
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Objects extends DelvingController with UserAuthentication with Secure {

  def objectUpdate(id: String): Html = html.add(id)

  def objectSubmit(data: String): Result = {
    val objectModel: ObjectModel = parse[ObjectModel](data)
    val persistedObject = objectModel.id match {
      case None =>
        val inserted: Option[ObjectId] = Object.insert(Object(TS_update = DateTime.now, name = objectModel.name, description = objectModel.description, user = getUserReference))
        if(inserted != None) Some(objectModel.copy(id = inserted)) else None
      case Some(id) =>
        val existingObject = Object.findOneByID(id)
        if(existingObject == None) Error("Object with id %s not found".format(id))
        val updatedObject = existingObject.get.copy(TS_update = DateTime.now, name = objectModel.name, description = objectModel.description, user = getUserReference)
        try {
          Object.update(MongoDBObject("_id" -> id), updatedObject, false, false, new WriteConcern())
          Some(objectModel)
        } catch {
          case e: SalatDAOUpdateError => None
          case _ => None
        }
    }

    persistedObject match {
      case Some(theObject) => Json(theObject)
      case None => Error("Error saving object")
    }
  }

}