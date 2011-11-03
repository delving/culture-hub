package controllers

import models.Story
import play.mvc.results.Result

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object Stories extends DelvingController {

  def list(user: Option[String], query: String, page: Int = 1): AnyRef = {

    // TODO access rights
    val storiesPage = user match {
      case Some(u) => Story.queryWithUser(query, browsedUserId).page(page)
      case None => Story.queryAll(query).page(page)
    }
    val items: List[ListItem] = storiesPage._1
    Template("/list.html", 'title -> listPageTitle("story"), 'itemName -> "story", 'items -> items, 'page -> page, 'count -> storiesPage._2)

  }

  def story(user: String, id: String): Result = {
    val story = Story.findById(id) getOrElse (return NotFound(&("user.stories.storyNotFound", id)))
    Template('story -> story)
  }

  def read(user: String, id: String): Result = {
    val story = Story.findById(id) getOrElse (return NotFound(&("user.stories.storyNotFound", id)))
    Template('story -> story)
  }

}