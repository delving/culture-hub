package controllers.user

import play.mvc.results.Result
import org.bson.types.ObjectId
import play.templates.Html
import views.User.Story._
import extensions.CHJson._
import models._
import com.mongodb.casbah.commons.MongoDBObject
import controllers.{ObjectModel, Secure, UserAuthentication, DelvingController}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Stories extends DelvingController with UserAuthentication with Secure {

  def load(id: String): Result = {

    val collections = UserCollection.findByUser(connectedUserId)
    val collectionVMs = (collections map { c => CollectionViewModel(c._id, c.name) }).toList

    Story.findById(id) match {
      case None => Json(StoryViewModel(collections = collectionVMs))
      case Some(story) =>
        val storyVM = StoryViewModel(id = Some(story._id),
          description = story.description,
          name = story.name,
          visibility = story.visibility.toString,
          isDraft = story.isDraft,
          pages = for (p <- story.pages) yield PageViewModel(title = p.title, text = p.text, objects = {
            val objects = DObject.findAllWithIds(p.objects map { _.dobject_id})
            (objects map { o => ObjectModel(id = Some(o._id), name = o.name, description = o.description, owner = o.user_id) }).toList
          }),
          collections = collectionVMs)

        Json(storyVM)
    }
  }

  def storyUpdate(id: String): Html = html.story(Option(id))

  def storySubmit(data: String): Result = {
    val storyVM = parse[StoryViewModel](data)
    val pages = storyVM.pages map {page => Page(page.title, page.text, page.objects map { o => PageObject(o.id.get) }) }
    val visibility = Visibility.withName(storyVM.visibility)

    val persistedStory = storyVM.id match {
      case None =>
        val story = Story(name = storyVM.name, description = storyVM.description, user_id = connectedUserId, visibility = storyVM.visibility, pages = pages, isDraft = storyVM.isDraft)
        val inserted = Story.insert(story)
        storyVM.copy(id = inserted)
      case Some(id) =>
        val savedStory = Story.findOneByID(id).getOrElse(return Error("Story with ID %s not found".format(id)))
        val updatedStory = savedStory.copy(name = storyVM.name, description = storyVM.description, visibility = storyVM.visibility, pages = pages)
        Story.save(updatedStory)
        storyVM
    }

    Json(persistedStory)
  }

}

case class StoryViewModel(id: Option[ObjectId] = None,
                          name: String = "",
                          description: String = "",
                          visibility: String = Visibility.Private.toString,
                          pages: List[PageViewModel] = List.empty[PageViewModel],
                          isDraft: Boolean = true,
                          collections: List[CollectionViewModel] = List.empty[CollectionViewModel])

case class PageViewModel(title: String = "", text: String = "", objects: List[ObjectModel] = List.empty[ObjectModel])

case class CollectionViewModel(id: ObjectId, name: String)