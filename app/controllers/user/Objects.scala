package controllers.user

import play.templates.Html
import views.User.Object._
import play.mvc.results.Result
import controllers.{ObjectModel, Secure, UserAuthentication, DelvingController}
import com.codahale.jerkson.Json._
import models.Object
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.WriteConcern

/**
 * Controller for manipulating user objects (creation, update, ...)
 * listing and display is done in the other controller that does not require authentication
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Objects extends DelvingController with UserAuthentication with Secure {

  def objectUpdate(id: String): Html = html.add(id)

  def objectSubmit(data: String): Result = {
    println(data)

    val objectModel: ObjectModel = parse[ObjectModel](data)
    val `object` = Object(name = objectModel.name, description = objectModel.description, user = getUserReference)

    val persistedObject = objectModel.id match {
      case None =>
        val inserted: Option[ObjectId] = Object.insert(`object`)
        if(inserted != None) Some(objectModel.copy(id = inserted)) else None
      case Some(id) =>
        // TODO handle the case when something goes wrong on the backend
        // TODO for cases where we update only some fields visible in the view, we need to do a merge of the persisted document and the changed field values by updated only those fields that we are touching in the view
        Object.update(MongoDBObject("_id" -> id), `object`, false, false, new WriteConcern())
        Some(objectModel)
    }

    persistedObject match {
      case Some(theObject) => Json(theObject)
      case None => Error("Error saving object")
    }
  }

}