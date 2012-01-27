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
import org.bson.types.ObjectId
import extensions.JJson
import user.ThumbnailLinking
import util.Constants._
import com.mongodb.casbah.Imports._
import models.{Link, Visibility, DObject, UserCollection}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController with ThumbnailLinking {

  def list(user: Option[String], page: Int = 1): Result = {
    val browser: (List[ListItem], Int) = Search.browse(USERCOLLECTION, user, request, theme)
    Template("/list.html", 'title -> listPageTitle("collection"), 'itemName -> "collection", 'items -> browser._1, 'page -> page, 'count -> browser._2)
  }

  def collection(user: String, id: String): Result = {
    UserCollection.findByIdUnsecured(id) match {
      case Some(thing) if (thing.visibility == Visibility.PUBLIC || thing.visibility == Visibility.PRIVATE && thing.userName == connectedUser) => {
        val objects: List[ListItem] = Search.search(None, request, theme, List("%s:%s OR %s:%s %s:%s".format(RECORD_TYPE, OBJECT, RECORD_TYPE, MDR, COLLECTIONS, id)))._1
        val labels: List[Token] = thing.freeTextLinks
        val places: List[Token] = thing.placeLinks
        val userLabels: List[Token] = if(thing.userName == connectedUser) labels else thing.freeTextLinks.filter(_.userName == connectedUser)
        val userPlaces: List[Token] = if(thing.userName == connectedUser) labels else thing.placeLinks.filter(_.userName == connectedUser)
        val theThing = if(thing.getBookmarksCollection) thing.copy(name = &("thing.bookmarksCollection"), description = &("thing.bookmarksCollectionDescription")) else thing
        Template('collection -> theThing, 'isBookmarksCollection -> thing.getBookmarksCollection, 'objects -> objects, 'labels -> JJson.generate(labels), 'labelsList -> labels, 'places -> JJson.generate(places), 'placesList -> places, 'userLabels -> JJson.generate(userLabels), 'userPlaces -> JJson.generate(userPlaces))
      }
      case _ => NotFound
    }
  }

  val NO_COLLECTION = "NO_COLLECTION"

  def listObjects(user: String, id: String): Result = {

    // unassigned objects
    if (id == NO_COLLECTION) {
      val shortObject: List[ShortObjectModel] = DObject.findAllUnassignedForUser(user)
      Json(shortObject)
    } else {
      if (!ObjectId.isValid(id)) return Error(&("collections.invalidCollectionId", id))
      val cid = new ObjectId(id)
      val userCollection = UserCollection.findOneByID(cid).getOrElse(return Error("Could not find collection " + cid))

      val userObjects: List[ShortObjectModel] = DObject.findAllWithCollection(cid)
      val linkedMdrs: List[ShortObjectModel] = userCollection.getLinkedMDRAccessors

      request.format match {
        case "json" => Json(userObjects ++ linkedMdrs)
        case _ => BadRequest
      }
    }
  }
}