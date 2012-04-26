package controllers.musip

import play.api.mvc.Action
import controllers.DelvingController
import core.rendering.ViewRenderer
import models.MusipItem
import play.api.i18n.Lang


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Show extends DelvingController {

  def museumViewDefinition = ViewRenderer.fromDefinition("musip", "museum")


  def collection(orgId: String, collection: String) = Root {
    Action {
      implicit request => Ok
    }
  }

  def museum(orgId: String, itemId: String) = Root {
    Action {
      implicit request => {

        MusipItem.find(orgId, itemId, "museum") match {
          case Some(museum) =>
            if(!museumViewDefinition.isDefined) {
              InternalServerError("Could not find museum view definition")
            } else {
              val renderResult = museumViewDefinition.get.renderRecord(museum.rawXml, List.empty, Map("musip" -> "http://www.musip.nl/"), Lang(getLang))
              val viewTree = renderResult.toViewTree

              Ok(Template("museum.html", 'view -> viewTree))
            }

          case None => NotFound("Museum not found")
        }
      }
    }
  }


}
