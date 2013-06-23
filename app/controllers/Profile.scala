package controllers

import play.api.mvc._
import models._
import play.api.i18n.Messages
import com.escalatesoft.subcut.inject.BindingModule

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class Profile(implicit val bindingModule: BindingModule) extends DelvingController {

  def profile(user: String): Action[AnyContent] = UserAction(user) {
    MultitenantAction {
      implicit request =>
        val u: HubUser = HubUser.dao.findByUsername(user) match {
          case Some(aUser) => aUser
          case None => return MultitenantAction { implicit request => NotFound(Messages("hub.UserWasNotFound", user)) }
        }

        Ok(Template(
          'user -> u,
          'isVisible -> (u.userProfile.isPublic || isConnected)
        ))
    }
  }
}