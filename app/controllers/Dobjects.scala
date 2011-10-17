package controllers

import models.DObject
import play.mvc.results.Result

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object DObjects extends DelvingController {

  def list(user: Option[String], query: String, page: Int = 1): AnyRef = {

    // TODO access rights
    val objectsPage = user match {
      case Some(u) => DObject.queryWithUser(query, browsedUserId).page(page)
      case None => DObject.queryAll(query).page(page)
    }

    request.format match {
      case "html" => views.html.list(title = listPageTitle("object"), itemName = "object", items = objectsPage._1, page = page, count = objectsPage._2)
      case "json" => Json(objectsPage._1)
    }
  }

  def dobject(user: String, id: String): Result = {
    DObject.findById(id) match {
        case None => NotFound
        case Some(anObject) => {
          val labels: List[ShortLabel] = anObject.labels
          Template('dobject -> anObject, 'labels -> labels)
        }
      }
  }

}