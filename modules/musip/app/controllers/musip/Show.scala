package controllers.musip

import controllers.DelvingController
import core.rendering.ViewRenderer
import models.MusipItem
import play.api.i18n.Lang
import play.api.mvc.{RequestHeader, Action}


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Show extends DelvingController {

  def museumViewDefinition = ViewRenderer.fromDefinition("musip", "museum")

  def collectionViewDefinition = ViewRenderer.fromDefinition("musip", "collection")


  def collection(orgId: String, itemId: String) = Root {
    Action {
      Action {
        implicit request => show(orgId, itemId, "collection", collectionViewDefinition) match {
          case Some((viewTree, systemFields)) => Ok(Template("show.html", 'view -> viewTree, 'systemFields -> systemFields))
          case None => NotFound("Collection not found")
        }
      }
    }
  }

  def museum(orgId: String, itemId: String) = Root {
    Action {
      implicit request => show(orgId, itemId, "museum", museumViewDefinition) match {
        case Some((viewTree, systemFields)) => Ok(Template("show.html", 'view -> viewTree, 'systemFields -> systemFields))
        case None => NotFound("Museum not found")
      }
    }
  }

  private def show(orgId: String, itemId: String, itemType: String, renderer: Option[ViewRenderer])(implicit request: RequestHeader) = {
    MusipItem.find(orgId, itemId, itemType).map {
      thing =>
        if (!renderer.isDefined) {
          InternalServerError("Could not find renderer for " + itemType)
        } else {
          val renderResult = renderer.get.renderRecord(thing.rawXml, List.empty, Map("musip" -> "http://www.musip.nl/"), Lang(getLang), Map("orgId" -> orgId))
          val viewTree = renderResult.toViewTree

          (viewTree, thing.systemFields)
        }
    }
  }


}
