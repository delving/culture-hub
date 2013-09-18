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

package models.cms

import com.mongodb.casbah.Imports.{ MongoCollection, MongoDB }
import com.mongodb.casbah.query.Imports._
import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import models.HubMongoContext._
import com.mongodb.casbah.commons.MongoDBObject
import models.{ OrganizationConfiguration, MultiModel }
import play.api.i18n.Lang

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class CMSPage(
  _id: ObjectId = new ObjectId(),
  key: String, // the key of this page (unique across all version sets of pages)
  userName: String, // who created the page
  lang: String, // 2-letters ISO code of the page language
  title: String, // title of the page in this language
  content: String, // actual page content (text)
  isSnippet: Boolean = false, // is this a snippet in the welcome page or not
  published: Boolean = false // is this page published, i.e. visible?
  )

case class MenuEntry(
  _id: ObjectId = new ObjectId(),
  menuKey: String, // key of the menu this entry belongs to
  parentMenuKey: Option[String] = None, // parent menu key. if none is provided this entry is not part of a sub-menu
  position: Int, // position of this menu entry inside of the menu
  title: Map[String, String], // title of this menu entry, per language
  targetPageKey: Option[String] = None, // key of the page this menu entry links to, if any
  targetMenuKey: Option[String] = None, // key of the target menu this entry links to, if any
  targetUrl: Option[String] = None, // URL this menu entry links to, if any
  published: Boolean = false // is this entry published, i.e. visible ?
  )

case class ListEntry(page: CMSPage, menuEntry: MenuEntry)

/** Represents a menu, which is not persisted at the time being **/
case class Menu(
  key: String,
  parentMenuKey: Option[String],
  title: Map[String, String])

object CMSPage extends MultiModel[CMSPage, CMSPageDAO] {
  def connectionName: String = "CMSPages"

  def initIndexes(collection: MongoCollection) {
    addIndexes(collection, Seq(MongoDBObject("_id" -> 1, "language" -> 1)))
  }

  def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: OrganizationConfiguration): CMSPageDAO = new CMSPageDAO(collection)
}

class CMSPageDAO(collection: MongoCollection)(implicit configuration: OrganizationConfiguration) extends SalatDAO[CMSPage, ObjectId](collection) {

  def entryList(lang: Lang, menuKey: Option[String]): List[ListEntry] = {
    val allEntries = MenuEntry.dao.findAll().toList
    val relevantEntries = if (menuKey.isEmpty) allEntries.filterNot(_.menuKey == "news") else allEntries.filter(_.menuKey == menuKey.get)
    val entries = relevantEntries.flatMap(
      menuEntry =>
        find(MongoDBObject(
          "lang" -> lang.language,
          "key" -> menuEntry.targetPageKey
        )).toList.map(
          page =>
            ListEntry(page, menuEntry)
        )
    )
    entries.groupBy(_.page.key).map(m => m._2.sortBy(_.page._id).reverse.head).toList
  }

  def findByKey(key: String): List[CMSPage] = find(MongoDBObject("key" -> key)).$orderby(MongoDBObject("_id" -> -1)).toList

  def findByKeyAndLanguage(key: String, lang: String): List[CMSPage] = find(queryByKeyAndLanguage(key, lang)).$orderby(MongoDBObject("_id" -> -1)).toList

  def create(key: String, lang: String, userName: String, title: String, content: String, published: Boolean): CMSPage = {
    val page = CMSPage(key = key, userName = userName, title = title, content = content, isSnippet = false, lang = lang, published = published)
    val inserted = insert(page)
    page.copy(_id = inserted.get)
  }

  def delete(key: String, lang: String) {
    remove(MongoDBObject("key" -> key, "lang" -> lang))
  }

  val MAX_VERSIONS = 20

  def removeOldVersions(key: String, lang: String) {
    val versions = findByKeyAndLanguage(key, lang)
    if (versions.length > MAX_VERSIONS) {
      val id = versions(MAX_VERSIONS - 1)._id

      find(("_id" $lt id) ++ queryByKeyAndLanguage(key, lang)).foreach(remove)

    }
  }

  private def queryByKeyAndLanguage(key: String, lang: String) = MongoDBObject("key" -> key, "lang" -> lang)

}

object MenuEntry extends MultiModel[MenuEntry, MenuEntryDAO] {

  def connectionName: String = "CMSMenuEntries"

  def initIndexes(collection: MongoCollection) {
    addIndexes(collection, Seq(MongoDBObject("menuKey" -> 1)))
    addIndexes(collection, Seq(MongoDBObject("menuKey" -> 1, "parentKey" -> 1)))
  }

  def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: OrganizationConfiguration): MenuEntryDAO = new MenuEntryDAO(collection)
}

class MenuEntryDAO(collection: MongoCollection) extends SalatDAO[MenuEntry, ObjectId](collection) {

  def findOneByKey(menuKey: String) = findOne(MongoDBObject("menuKey" -> menuKey))

  def findOneByMenuKeyAndTargetMenuKey(menuKey: String, targetMenuKey: String) = findOne(MongoDBObject("menuKey" -> menuKey, "targetMenuKey" -> targetMenuKey))

  def findOneByTargetPageKey(targetPageKey: String) = findOne(MongoDBObject("targetPageKey" -> targetPageKey))

  def findOneByMenuAndPage(orgId: String, menuKey: String, pageKey: String) = findOne(MongoDBObject("orgId" -> orgId, "menuKey" -> menuKey, "targetPageKey" -> pageKey))

  def findEntries(orgId: String, menuKey: String, parentKey: String) = {
    find(MongoDBObject("orgId" -> orgId, "menuKey" -> menuKey, "parentKey" -> parentKey)).$orderby(MongoDBObject("position" -> 1))
  }

  def findEntries(menuKey: String) = find(MongoDBObject("menuKey" -> menuKey)).$orderby(MongoDBObject("position" -> 1))

  def findAll() = find(MongoDBObject())

  /**
   * Adds a page to a menu (root menu). If the menu entry already exists, updates the position and title.
   */
  def savePage(menuKey: String, targetPageKey: String, position: Int, title: String, lang: String, published: Boolean) {
    findOneByTargetPageKey(targetPageKey) match {
      case Some(existing) =>
        val updatedEntry = existing.copy(position = position, menuKey = menuKey, title = existing.title + (lang -> title), published = published)
        save(updatedEntry)
        // update position of siblings by shifting them to the right
        update(MongoDBObject("menuKey" -> menuKey, "published" -> published) ++ ("position" $gte (position)) ++ ("targetPageKey" $ne (targetPageKey)), $inc("position" -> 1))
      case None =>
        val newEntry = MenuEntry(menuKey = menuKey, parentMenuKey = None, position = position, targetPageKey = Some(targetPageKey), title = Map(lang -> title), published = published)
        insert(newEntry)
    }
  }

  def removePage(targetPageKey: String, lang: String) {
    findOne(MongoDBObject("targetPageKey" -> targetPageKey)) match {
      case Some(entry) =>
        val updated = entry.copy(title = (entry.title.filterNot(_._1 == lang)))
        if (updated.title.isEmpty) {
          removeById(updated._id)
        } else {
          save(updated)
        }
      case None => // nothing
    }
  }

}