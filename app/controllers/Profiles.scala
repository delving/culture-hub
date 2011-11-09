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

    val objects: List[ListItem] = DObject.browseByUser(u._id, connectedUserId).toList
    val collections: List[ListItem] = UserCollection.browseByUser(u._id, connectedUserId).toList
    val stories: List[ListItem] = Story.browseByUser(u._id, connectedUserId).toList

    Template('user -> u, 'objects -> objects, 'collections -> collections, 'stories -> stories)
  }
}
