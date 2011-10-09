package controllers

import models._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Profiles extends DelvingController {

  import views.Profile._

  def view(user: String): AnyRef = {
    val u: ShortUser = getUser(user) match {
      case Right(aUser) => aUser
      case Left(error) => return error
    }

    val objects: List[ListItem] = DObject.findByUser(u.id).toList
    val collections: List[ShortCollection] = UserCollection.findByUser(u.id).toList
    val stories: List[ShortStory] = Story.findByUser(u.id).toList

    html.view(u, objects, collections, stories)
  }
}
