/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.user

import play.mvc.results.Result
import org.bson.types.ObjectId
import extensions.JJson._
import models._
import controllers._
import play.data.validation.Annotations._
import java.util.Date
import extensions.JJson
import util.Constants._
import views.context.DEFAULT_THUMBNAIL
import components.IndexingService

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Stories extends DelvingController with UserSecured with UGCController {

  private def load(id: String): String = {

    val collections = UserCollection.browseByUser(connectedUserId, connectedUserId)
    val collectionVMs = (collections map { c => CollectionReference(c._id, c.name) }).toList ++ List(CollectionReference(controllers.Collections.NO_COLLECTION, &("user.story.noCollection")))

    Story.findById(id, connectedUserId) match {
      case None => JJson.generate(StoryViewModel(collections = collectionVMs))
      case Some(story) =>
        
        val storyVM = StoryViewModel(id = Some(story._id),
          description = story.description,
          name = story.name,
          visibility = story.visibility.value,
          isDraft = story.isDraft,
          thumbnail = story.getThumbnailIdInternal,
          pages = for (p <- story.pages) yield PageViewModel(title = p.title, text = p.text, objects = p.getPageObjects),
          collections = collectionVMs)

      JJson.generate(storyVM)
    }
  }

  def story(id: String): Result = {
    renderArgs += ("viewModel", classOf[StoryViewModel])
    Template('id -> Option(id), 'data -> load(id))
  }

  def storySubmit(data: String): Result = {
    
    def findThumbnailUrl(thumbnailId: String) = {
      thumbnailId match {
        case oid if ObjectId.isValid(oid) => None // it's an object ID
        case hubId if hubId.count(_ == '_') == 2 => MetadataRecord.getMDR(hubId) match {
          case Some(m) => Some(m.getDefaultAccessor.getThumbnailUri)
          case None => None
        }
        case _ => Some(DEFAULT_THUMBNAIL)
      }
    }
    
    val storyVM = parse[StoryViewModel](data)

    validate(storyVM).foreach { errors => return JsonBadRequest(storyVM.copy(errors = errors)) }

    val pages = storyVM.pages map {
      page =>
        Page(page.title,  page.text,
          page.objects map {
            o => o match {
              case obj if obj.hubType == OBJECT => PageObject(objectId = obj.id)
              case obj if obj.hubType == MDR => PageObject(hubId = Some(obj.id))
            }
          })
      }

    val persistedStory = storyVM.id match {
      case None =>
        val story = Story(
          name = storyVM.name,
          TS_update = new Date(),
          description = storyVM.description,
          user_id = connectedUserId,
          userName = connectedUser,
          visibility = Visibility.get(storyVM.visibility.intValue()),
          thumbnail_id = None,
          thumbnail_url = None,
          pages = pages,
          isDraft = storyVM.isDraft)
        val inserted = Story.insert(story)
        inserted match {
          case Some(iid) =>
            // set thumbnail
            setThumbnaiL(iid, STORY, storyVM.thumbnail, findThumbnailUrl(storyVM.thumbnail), None)

            if(!story.isDraft) {
              IndexingService.index(Story.findOneByID(iid).getOrElse(return Error(&("user.stories.storyNotFound", iid))))
            }
            storyVM.copy(id = inserted)
          case None => None
        }
      case Some(id) =>
        val savedStory = Story.findOneByID(id).getOrElse(return Error(&("user.stories.storyNotFound", id)))
        val updatedStory = savedStory.copy(
          TS_update = new Date(),
          name = storyVM.name,
          description = storyVM.description,
          visibility = Visibility.get(storyVM.visibility.intValue()),
          isDraft = storyVM.isDraft,
          pages = pages)
        Story.save(updatedStory)

        // update thumbnail
        setThumbnaiL(id, STORY, storyVM.thumbnail, findThumbnailUrl(storyVM.thumbnail), updatedStory.links.filter(_.linkType == Link.LinkType.THUMBNAIL).map(_.link).headOption)

        // index in solr
        IndexingService.index(Story.findOneByID(id).getOrElse(return Error(&("user.stories.storyNotFound", id))))
        storyVM
    }
    Json(persistedStory)
  }

  def remove(id: ObjectId) = {
    if(Story.owns(connectedUserId, id)) {
      Story.delete(id)
      IndexingService.deleteById(id)
    } else Forbidden("Big brother is watching you")
  }

}

case class StoryViewModel(id: Option[ObjectId] = None,
                          @Required name: String = "",
                          @Required description: String = "",
                          visibility: Integer = Visibility.PRIVATE.value,
                          pages: List[PageViewModel] = List.empty[PageViewModel],
                          isDraft: Boolean = true,
                          thumbnail: String = "",
                          collections: List[CollectionReference] = List.empty[CollectionReference],
                          errors: Map[String, String] = Map.empty[String, String]) extends ViewModel

case class PageViewModel(title: String = "", text: String = "", objects: List[ShortObjectModel] = List.empty[ShortObjectModel])
