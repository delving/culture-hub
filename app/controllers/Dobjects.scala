package controllers

import play.mvc.results.Result
import org.bson.types.ObjectId
import models.UserReference

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object Dobjects extends DelvingController {

  import views.Dobject._

  def list(user: String): AnyRef = {
    val u = getUser(user)
    html.list(user = u)
  }

  def view(user: String, id: String): AnyRef = {
    id match {
      case null => NotFound
      case objectId if !ObjectId.isValid(objectId) => NotFound("Invalid object id %s".format(id))
      case objectId =>
        val o = models.Object.findOneByID(new ObjectId(id)) // TODO access rights
        if (o == None) NotFound("Object with id %s was not found".format(id))
        html.dobject(dobject = o.get)
    }
  }

  def load(id: String): Result = {
    id match {
      case null => Json(ObjectModel.empty)
      case objectId if !ObjectId.isValid(objectId) => Error("Invalid object id %s".format(objectId))
      case objectId =>
        val o = models.Object.findOneByID(new ObjectId(id)) // TODO access rights
        if (o == None) NotFound("Object with id %s was not found".format(id))
        val dobject = o.get
        Json(ObjectModel(Some(dobject._id), dobject.name, dobject.description, dobject.user))
    }
  }
}

case class ObjectModel(id: Option[ObjectId] = None, name: String = "", description: Option[String] = Some(""), owner: UserReference)

object ObjectModel {
  val empty: ObjectModel = ObjectModel(name = "", owner = UserReference("", ""))
}