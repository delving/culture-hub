package controllers

import play.mvc.results.Result
import models.{Visibility, DObject}
import extensions.JJson
import util.Constants._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object DObjects extends DelvingController {

  def list(user: Option[String], page: Int = 1): AnyRef = {
    val browser: (List[ListItem], Int) = Search.browse(OBJECT, user, request, theme)
    request.format match {
      case "html" => Template("/list.html", 'title -> listPageTitle("object"), 'itemName -> OBJECT, 'items -> browser._1, 'page -> page, 'count -> browser._2)
      case "json" => Json(browser._1)
    }
  }

  def dobject(user: String, id: String): Result = {
    DObject.findByIdUnsecured(id) match {
        case Some(thing) if (thing.visibility == Visibility.PUBLIC || thing.visibility == Visibility.PRIVATE && thing.user_id == connectedUserId) => {
          val labels: List[Token] = thing.freeTextLinks
          val places: List[Token] = thing.placeLinks
          Template('dobject -> thing, 'labels -> JJson.generate(labels), 'labelsList -> labels, 'places -> JJson.generate(places), 'placesList -> places)
        }
        case _ => NotFound
    }
  }

}