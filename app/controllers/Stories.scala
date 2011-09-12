package controllers

import org.bson.types.ObjectId
import models.{Story, DObject}
import org.joda.time.DateTime
import play.templates.Html

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object Stories extends DelvingController {

  import views.Story._

  def list(user: Option[String], query: String, page: Int = 1): AnyRef = {

    // TODO access rights
    val storiesPage = user match {
      case Some(u) => Story.findByUser(browsedUserId).page(page)
      case None => Story.findAll.page(page)
    }

    html.list(stories = storiesPage._1, page = page, count = storiesPage._2)

  }

  def story(user: String, story: String): AnyRef = {
    val u = getUser(user)
    html.story(user = u, name = story)
  }

  def read(user: String, id: String): AnyRef = {
    val story = Story.findById(id) getOrElse(return NotFound("Story with ID %s not found".format(id)))
    html.storyRead(story)
  }

}

// ~~~ list page models

case class ShortStory(id: ObjectId, TS_update: DateTime, name: String, shortDescription: String, thumbnail: Option[ObjectId], userName: String)
