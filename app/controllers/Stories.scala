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

package controllers

import play.mvc.results.Result
import models.{Visibility, Story}
import extensions.JJson
import util.Constants._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object Stories extends DelvingController {

  def list(user: Option[String], page: Int = 1): AnyRef = {
    val browser: (List[ListItem], Int) = Search.browse(STORY, user, request, theme)
    Template("/list.html", 'title -> listPageTitle("story"), 'itemName -> STORY, 'items -> browser._1, 'page -> page, 'count -> browser._2)
  }

  def story(user: String, id: String): Result = {
    Story.findByIdUnsecured(id) match {
      case Some(thing) if (thing.visibility == Visibility.PUBLIC || thing.visibility == Visibility.PRIVATE && thing.user_id == connectedUserId) =>
        val labels: List[Token] = thing.freeTextLinks
        val places: List[Token] = thing.placeLinks
        val userLabels: List[Token] = if(thing.userName == connectedUser) labels else thing.freeTextLinks.filter(_.userName == connectedUser)
        val userPlaces: List[Token] = if(thing.userName == connectedUser) labels else thing.placeLinks.filter(_.userName == connectedUser)
        Template('story -> thing, 'labels -> JJson.generate(labels), 'labelsList -> labels, 'places -> JJson.generate(places), 'placesList -> places, 'userLabels -> JJson.generate(userLabels), 'userPlaces -> JJson.generate(userPlaces))
      case _ => NotFound(&("user.stories.storyNotFound", id))
    }
  }

  def read(user: String, id: String): Result = {
    Story.findByIdUnsecured(id) match {
      case Some(thing) if (thing.visibility == Visibility.PUBLIC || thing.visibility == Visibility.PRIVATE && thing.user_id == connectedUserId) =>
        Template('story -> thing)
      case _ => NotFound(&("user.stories.storyNotFound", id))
    }
  }

}