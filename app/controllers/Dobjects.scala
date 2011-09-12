package controllers

import org.bson.types.ObjectId
import models.{DObject}
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
      case Some(u) => DObject.queryWithUser(query, browsedUserId).page(page)
      case None => DObject.queryAll(query).page(page)
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

}

// ~~~ list page models

case class ShortObject(id: ObjectId, TS_update: DateTime, name: String, shortDescription: String, userName: String)