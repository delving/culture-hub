package controllers

import play.mvc.results.Result
import models.{Visibility, DObject}
import extensions.JJson

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object DObjects extends DelvingController {

  def list(user: Option[String], page: Int = 1): AnyRef = {

    val objectsPage = user match {
      case Some(u) => DObject.browseByUser(browsedUserId, connectedUserId).page(page)
      case None => DObject.browseAll(connectedUserId).page(page)
    }

    val items: List[ListItem] = objectsPage._1
    request.format match {
      case "html" => Template("/list.html", 'title -> listPageTitle("object"), 'itemName -> "object", 'items -> items, 'page -> page, 'count -> objectsPage._2)
      case "json" => Json(items)
    }
  }

  def dobject(user: String, id: String): Result = {
    DObject.findByIdUnsecured(id) match {
        case Some(thing) if (thing.visibility == Visibility.PUBLIC || thing.visibility == Visibility.PRIVATE && thing.user_id == connectedUserId) => {
          val labels = thing.labels.map(l => (Token(l.link, l.value.label))).toList
          Template('dobject -> thing, 'labels -> JJson.generate(labels))
        }
        case _ => NotFound
    }
  }

}