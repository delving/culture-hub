package controllers

import play.mvc.results.Result
import org.bson.types.ObjectId
import org.codehaus.jackson.annotate.JsonCreator

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

  def dobject(user: String, dobject: String): AnyRef = {
    val u = getUser(user)
    html.dobject(user = u, name = dobject)
  }

  def load(id: String): Result = {
    id match {
      case null => Json(ObjectModel.empty)
      case objectId if !ObjectId.isValid(objectId) => Error("Invalid object id %s".format(objectId))
      case objectId =>
        val o = models.Object.findOneByID(new ObjectId(id)) // TODO access rights
        if (o == None) NotFound("Object with id " + id + " was not found")
        Json(ObjectModel(Some(o.get._id), o.get.name, o.get.description))
    }
  }
}

case class ObjectModel(id: Option[ObjectId] = None, name: String = "", description: Option[String] = Some(""))

object ObjectModel {
  val empty: ObjectModel = ObjectModel(name = "")
}
