package controllers

import play.mvc.results.Result
import models.{Visibility, DObject}

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
    DObject.findById(id) match {
        case Some(thing) if (thing.visibility == Visibility.PUBLIC || thing.visibility == Visibility.PRIVATE && thing.user_id == connectedUserId) => {
          val labels: List[ShortLabel] = thing.labels
          Template('dobject -> thing, 'labels -> labels)
        }
        case _ => NotFound
    }
  }

}