package controllers

import models._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Profile extends DelvingController {

  def profile(user: String): AnyRef = {
    val u: User = getUser(user) match {
      case Right(aUser) => aUser
      case Left(error) => return error
    }

    if(u.userProfile.isPublic || !u.userProfile.isPublic && isConnected) {
      val objectsPage: (List[DObject], Int) = DObject.findPublicByUser(u.userName).page(1, viewUtils.themeProperty("profileObjectsCount", classOf[Int]))
      val collectionsPage: (List[UserCollection], Int) = UserCollection.findPublicByUser(u.userName).page(1, viewUtils.themeProperty("profileCollectionsCount", classOf[Int]))
      val storyPage: (List[Story], Int) = Story.findPublicByUser(u.userName).page(1, viewUtils.themeProperty("profileStoriesCount", classOf[Int]))

      val objects: List[ListItem] = objectsPage._1
      val collections: List[ListItem] = collectionsPage._1
      val stories: List[ListItem] = storyPage._1

      Template('user -> u, 'objects -> objects, 'objectsCount -> objectsPage._2, 'collections -> collections, 'collectionsCount -> collectionsPage._2, 'stories -> stories, 'storiesPage -> collectionsPage._2)
    } else {
      NotFound(&("profile.profileNotFound", user))
    }

  }
}
