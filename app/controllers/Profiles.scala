package controllers

import models._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Profiles extends DelvingController {

  def view(user: String): AnyRef = {
    val u: ShortUser = getUser(user) match {
      case Right(aUser) => aUser
      case Left(error) => return error
    }

    val objects: List[ListItem] = DObject.findByUser(u.id).toList
    val collections: List[ShortCollection] = UserCollection.findByUser(u.id).toList
    val stories: List[ShortStory] = Story.findByUser(u.id).toList

    Template('user -> u, 'objects -> objects, 'collections -> collections, 'stories -> stories)
  }
}
