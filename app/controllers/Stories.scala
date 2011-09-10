package controllers

import org.bson.types.ObjectId
import models.{Story, DObject}

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

    html.list(stories = storiesPage._1 map { s => ShortStory(s._id, s.name, s.description, "", s.userName) }, page = page, count = storiesPage._2)

  }

  def story(user: String, story: String): AnyRef = {
    val u = getUser(user)
    html.story(user = u, name = story)
  }

}

// ~~~ list page models

case class ShortStory(id: ObjectId, name: String, shortDescription: String, thumbnailUrl: String, userName: String)
