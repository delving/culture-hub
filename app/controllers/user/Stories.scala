package controllers.user

import play.mvc.results.Result
import controllers.{Secure, UserAuthentication, DelvingController}
import org.bson.types.ObjectId
import play.templates.Html
import views.User.Story._
import models.{UserCollection, Status, Story}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Stories extends DelvingController with UserAuthentication with Secure {

  def load(id: String): Result = {

    val collections = UserCollection.findByUser(connectedUserId)
    val collectionVMs = for (c <- collections) yield CollectionViewModel(c._id, c.name)

    Story.findById(id) match {
      case None => Json(StoryViewModel(collections = collectionVMs))
      case Some(story) =>
        val storyVM = StoryViewModel(id = Some(story._id),
          description = story.description,
          name = story.name,
          visibility = story.visibility,
          pages = for (p <- story.pages) yield PageViewModel(p.title, p.text, for (o <- p.objects) yield o.dobject_id),
          collections = collectionVMs)

        Json(storyVM)
    }
  }

  def storyUpdate(id: String): Html = html.story(Option(id))

  def storySubmit: Result = Ok

}

case class StoryViewModel(id: Option[ObjectId] = None,
                          name: String = "",
                          description: String = "",
                          visibility: Status.Value = Status.Private,
                          pages: List[PageViewModel] = List.empty[PageViewModel],
                          collections: List[CollectionViewModel] = List.empty[CollectionViewModel])

case class PageViewModel(title: String = "", text: String = "", objects: List[ObjectId] = List.empty[ObjectId])

case class CollectionViewModel(id: ObjectId, name: String)