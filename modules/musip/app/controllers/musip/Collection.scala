package controllers.musip

import play.api.mvc.Action
import controllers.DelvingController


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collection extends DelvingController {

  /**
   * Collection profile view
   */
  def collection(orgId: String, collection: String) = Root {
    Action {
      implicit request => Ok
    }
  }

}
