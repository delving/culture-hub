package controllers.user

import play.mvc.results.Result
import org.bson.types.ObjectId
import play.templates.Html
import views.User.Story._
import extensions.CHJson._
import models._
import org.scala_tools.time.Imports._
import controllers._
import play.data.validation.Annotations._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Stories extends DelvingController with UserAuthentication with Secure {

  implicit val viewModel = Some(classOf[StoryViewModel])

  def load(id: String): Result = {

    val collections = UserCollection.findByUser(connectedUserId)
    val collectionVMs = (collections map { c => CollectionReference(c._id, c.name) }).toList

    Story.findById(id) match {
      case None => Json(StoryViewModel(collections = collectionVMs))
      case Some(story) =>
        val storyVM = StoryViewModel(id = Some(story._id),
          description = story.description,
          name = story.name,
          visibility = story.visibility.toString,
          isDraft = story.isDraft,
          thumbnail = story.thumbnail,
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
    validate(storyVM).foreach { errors => return JsonBadRequest(storyVM.copy(errors = errors)) }

    val pages = storyVM.pages map {page => Page(page.title, page.text, page.objects map { o => PageObject(o.id.get) })}
    val visibility = Visibility.withName(storyVM.visibility)
    val thumbnail = if (ObjectId.isValid(storyVM.thumbnail)) Some(new ObjectId(storyVM.thumbnail)) else None

    val persistedStory = storyVM.id match {
      case None =>
        val story = Story(name = storyVM.name, TS_update = DateTime.now, description = storyVM.description, user_id = connectedUserId, userName = connectedUser, visibility = visibility, thumbnail = thumbnail, pages = pages, isDraft = storyVM.isDraft)
        val inserted = Story.insert(story)
        storyVM.copy(id = inserted)
      case Some(id) =>
        val savedStory = Story.findOneByID(id).getOrElse(return Error("Story with ID %s not found".format(id)))
        val updatedStory = savedStory.copy(TS_update = DateTime.now, name = storyVM.name, description = storyVM.description, visibility = visibility, thumbnail = thumbnail, isDraft = storyVM.isDraft, pages = pages)
        Story.save(updatedStory)
        storyVM
    }

    Json(persistedStory)
  }

}

case class StoryViewModel(id: Option[ObjectId] = None,
                          @Required name: String = "",
                          description: String = "",
                          visibility: String = Visibility.Public.toString,
                          pages: List[PageViewModel] = List.empty[PageViewModel],
                          isDraft: Boolean = true,
                          thumbnail: String = "",
                          collections: List[CollectionReference] = List.empty[CollectionReference],
                          errors: Map[String, String] = Map.empty[String, String]) extends ViewModel

case class PageViewModel(title: String = "", text: String = "", objects: List[ObjectModel] = List.empty[ObjectModel])