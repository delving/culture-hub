/*
 * Copyright 2012 Delving B.V.
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

package models

import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import com.mongodb.casbah.commons.MongoDBObject

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class CMSPage(_id: ObjectId = new ObjectId(),
                   key: String, // the key of this page (unique across all version sets of pages)
                   userName: String, // who created the page
                   orgId: String, // orgId to which this page belongs to
                   theme: String, // hub theme the page belongs to
                   lang: String, // 2-letters ISO code of the page language
                   title: String, // title of the page in this language
                   content: String, // actual page content (text)
                   isSnippet: Boolean = false // is this a snippet in the welcome page or not
                  )

case class MenuEntry(_id: ObjectId = new ObjectId(),
                     orgId: String, // orgId to which this menu belongs to
                     theme: String,  // hub theme the menu entry belongs to
                     menuKey: String, // key of the menu this entry belongs to
                     parentKey: Option[ObjectId] = None, // parent menu entry key. if none is provided this entry is not part of a sub-menu
                     position: Int, // position of this menu entry inside of the menu
                     title: Map[String, String], // title of this menu entry, per language
                     targetPageKey: Option[String] = None, // key of the page this menu entry links to, if any
                     targetUrl: Option[String] = None, // URL this menu entry links to, if any
                     targetAnchor: Option[String] = None // id of the target HTML anchor, if any
                    )


object CMSPage extends SalatDAO[CMSPage, ObjectId](cmsPages) {

  def list(orgId: String, lang: String): List[CMSPage] = find(MongoDBObject("orgId" -> orgId, "lang" -> lang)).toList.groupBy(_.key).map(m => m._2.sortBy(_._id.getTime).head).toList
  
  def findByKey(orgId: String, key: String): List[CMSPage] = find(MongoDBObject("orgId" -> orgId, "key" -> key)).$orderby(MongoDBObject("_id" -> -1)).toList

  def findByKeyAndLanguage(key: String, lang: String): List[CMSPage] = find(MongoDBObject("key" -> key, "lang" -> lang)).$orderby(MongoDBObject("_id" -> -1)).toList
  
  def create(orgId: String, theme: String, key: String, lang: String, userName: String, title: String, content: String): CMSPage = {
    val page = CMSPage(orgId = orgId, theme = theme, key = key, userName = userName, title = title, content = content, isSnippet = false, lang = lang)
    val inserted = CMSPage.insert(page)
    page.copy(_id = inserted.get)
  }

  def delete(orgId: String, key: String, lang: String) {
    remove(MongoDBObject("orgId" -> orgId, "key" -> key, "lang" -> lang))
  }
  
}

object MenuEntry extends SalatDAO[MenuEntry, ObjectId](cmsMenuEntries) {

  def findByPageAndMenu(orgId: String, menuKey: String, key: String) = findOne(MongoDBObject("orgId" -> orgId, "menuKey" -> menuKey, "targetPageKey" -> key))
  
  def findEntries(orgId: String, theme: String, menuKey: String, parentKey: Option[ObjectId] = None) = find(MongoDBObject("orgId" -> orgId, "theme" -> theme, "menuKey" -> menuKey, "parentKey" -> parentKey)).$orderby(MongoDBObject("position" -> 1))

  // TODO FIXME this won't scale when more orgs live on one culturehub. we need to fix the theme -> orgId lookup
  def findEntries(theme: String, menuKey: String) = find(MongoDBObject("theme" -> theme, "menuKey" -> menuKey)).$orderby(MongoDBObject("position" -> 1))

  /**
   * Adds a page to a menu (root menu). If the menu entry already exists, updates the position and title.
   */
  def addPage(orgId: String, theme: String, menuKey: String, targetPageKey: String, position: Int, title: String, lang: String) {
    findByPageAndMenu(orgId, menuKey, targetPageKey) match {
      case Some(existing) =>
        val updatedEntry = existing.copy(position = position, title = existing.title + (lang -> title))
        save(updatedEntry)
        // update position of siblings by shifting them to the right
        update(MongoDBObject("orgId" -> orgId, "menuKey" -> menuKey) ++ "position" $gte (position) ++ "targetPageKey" $ne (targetPageKey), $inc("position" -> 1))
      case None =>
        val newEntry = MenuEntry(orgId = orgId, theme = theme, menuKey = menuKey, parentKey = None, position = position, targetPageKey = Some(targetPageKey), title = Map(lang -> title))
        insert(newEntry)
    }
  }

  def updatePosition(orgId: String, menuKey: String, parentKey: Option[ObjectId] = None, position: Int) {
    update(MongoDBObject("orgId" -> orgId, "menuKey" -> menuKey, "parentKey" -> parentKey), $set("position" -> position))
  }

  def updateTitle(orgId: String, menuKey: String, parentKey: Option[ObjectId] = None, lang: String, title: String) {
    update(MongoDBObject("orgId" -> orgId, "menuKey" -> menuKey, "parentKey" -> parentKey), $set("title.%s".format(lang) -> title))
  }
  
}