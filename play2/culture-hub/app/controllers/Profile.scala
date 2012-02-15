package controllers

import play.api.mvc._
import models._
import core.ThemeInfo
import play.api.i18n.Messages

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Profile extends DelvingController {

  def profile(user: String): Action[AnyContent] = UserAction(user) {
    Action {
      implicit request =>
        val u: User = User.findByUsername(user) match {
          case Some(aUser) => aUser
          case None => return Action { implicit request => NotFound(Messages("delvingcontroller.userNotFound", user)) }
        }

        val themeInfo = new ThemeInfo(theme)

        val objectsPage: (List[DObject], Int) = DObject.findVisibleByUser(u.userName, connectedUser).page(1, themeInfo.themeProperty("profileObjectsCount", classOf[Int]))
        val collectionsPage: (List[UserCollection], Int) = UserCollection.findVisibleByUser(u.userName, connectedUser).page(1, themeInfo.themeProperty("profileCollectionsCount", classOf[Int]))
        val storyPage: (List[Story], Int) = Story.findVisibleByUser(u.userName, connectedUser).page(1, themeInfo.themeProperty("profileStoriesCount", classOf[Int]))

        val objects: List[ListItem] = objectsPage._1
        val collections: List[ListItem] = collectionsPage._1
        val stories: List[ListItem] = storyPage._1

        Ok(Template(
          'user -> u,
          'objects -> objects,
          'objectsCount -> objectsPage._2,
          'collections -> collections,
          'collectionsCount -> collectionsPage._2,
          'stories -> stories,
          'storiesPage -> collectionsPage._2,
          'isVisible -> (u.userProfile.isPublic || isConnected)
        ))
      }
    }
}
