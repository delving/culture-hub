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
import util.Constants._
import models.{CMSPage, DObject, Story, UserCollection}
import play.i18n.Lang
import com.mongodb.casbah.commons.MongoDBObject

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Application extends DelvingController {

  def index: Result = {

    val recentCollections: List[ListItem] = UserCollection.findRecent(viewUtils.themeProperty("recentCollectionsCount", classOf[Int])).toList
    val recentStories: List[ListItem] = Story.findRecent(viewUtils.themeProperty("recentStoriesCount", classOf[Int])).toList
    val recentObjects: List[ListItem] = DObject.findRecent(viewUtils.themeProperty("recentObjectsCount", classOf[Int])).toList
    val recentMdrs: List[ListItem] = Search.search(None, request, theme, List("%s:%s AND %s:%s".format(RECORD_TYPE, MDR, HAS_DIGITAL_OBJECT, true)))._1.slice(0, viewUtils.themeProperty("recentMdrsCount", classOf[Int]))

    Template('recentCollections -> recentCollections, 'recentStories -> recentStories, 'recentObjects -> recentObjects, 'recentMdrs -> recentMdrs)
  }
  
  def page(key: String): Result = {
    // TODO link the themes to the organization so this also works on multi-org hubs
    CMSPage.findOne(MongoDBObject("key" -> key, "lang" -> Lang.get(), "theme" -> theme.name)) match {
      case None => NotFound
      case Some(page) => Template('page -> page)
    }
    
  }

}