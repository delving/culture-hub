package controllers.musip

import play.api.mvc.{Action, Controller}


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collection extends Controller {

  /**
   * Collection profile view
   */
  def collection(orgId: String, collection: String) = Action {
    implicit request => Ok

  }

}
