package controllers

import play.api.mvc._
import models._
import play.api.i18n.Messages

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Profile extends DelvingController {

  def profile(user: String): Action[AnyContent] = UserAction(user) {
    Action {
      implicit request =>
        val u: HubUser = HubUser.dao.findByUsername(user) match {
          case Some(aUser) => aUser
          case None => return Action { implicit request => NotFound(Messages("hub.UserWasNotFound", user)) }
        }

        Ok(Template(
          'user -> u,
          'isVisible -> (u.userProfile.isPublic || isConnected)
        ))
    }
  }
}