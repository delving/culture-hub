package controllers

import play.mvc.results.Result
import models.{Visibility, Story}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object Stories extends DelvingController {

  def list(user: Option[String], page: Int = 1): AnyRef = {

    val storiesPage = user match {
      case Some(u) => Story.browseByUser(browsedUserId, connectedUserId).page(page)
      case None => Story.browseAll(connectedUserId).page(page)
    }
    val items: List[ListItem] = storiesPage._1
    Template("/list.html", 'title -> listPageTitle("story"), 'itemName -> "story", 'items -> items, 'page -> page, 'count -> storiesPage._2)

  }

  def story(user: String, id: String): Result = {
    Story.findById(id) match {
      case Some(thing) if (thing.visibility == Visibility.PUBLIC || thing.visibility == Visibility.PRIVATE && thing.user_id == connectedUserId) =>
        Template('story -> thing)
      case _ => NotFound(&("user.stories.storyNotFound", id))
    }
  }

  def read(user: String, id: String): Result = {
    Story.findById(id) match {
      case Some(thing) if (thing.visibility == Visibility.PUBLIC || thing.visibility == Visibility.PRIVATE && thing.user_id == connectedUserId) =>
        Template('story -> thing)
      case _ => NotFound(&("user.stories.storyNotFound", id))
    }
  }

}