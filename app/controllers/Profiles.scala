package controllers

import models._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Profiles extends DelvingController {

  def view(user: String): AnyRef = {
    val u: User = getUser(user) match {
      case Right(aUser) => aUser
      case Left(error) => return error
    }

    val objects: List[ListItem] = DObject.findByUser(u._id).toList
    val collections: List[ListItem] = UserCollection.findByUser(u._id).toList
    val stories: List[ListItem] = Story.findByUser(u._id).toList

    Template('user -> u, 'objects -> objects, 'collections -> collections, 'stories -> stories)
  }
}
