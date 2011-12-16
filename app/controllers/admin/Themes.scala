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

package controllers.admin

import play.mvc.results.Result
import extensions.JJson
import org.bson.types.ObjectId
import util.ThemeHandler
import com.mongodb.casbah.commons.MongoDBObject
import controllers.{ViewModel, DelvingController}
import models.{EmailTarget, PortalTheme}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Themes extends DelvingController with AdminSecure {

  def index(): Result = {
    val themeList = PortalTheme.find(MongoDBObject())
    Template('themes -> themeList.toList)
  }

  def load(id: String): Result = {
    PortalTheme.findOneByID(new ObjectId(id)) match {
      case None => Json(ThemeViewModel())
      case Some(theme) => Json(ThemeViewModel(
        id = Some(theme._id),
        name = theme.name,
        localisedQueryKeys = theme.localiseQueryKeys,
        hiddenQueryFilter = theme.hiddenQueryFilter,
        subdomain = theme.subdomain,
        defaultLanguage = theme.defaultLanguage,
        solrSelectUrl = theme.solrSelectUrl,
        cacheUrl = theme.cacheUrl,
        emailTarget = theme.emailTarget,
        homePage = theme.homePage,
        possibleQueryKeys = theme.localiseQueryKeys)
      )
    }
  }

  def list(): AnyRef = {
    val themeList = PortalTheme.find(MongoDBObject())
    Json(Map("themes" -> themeList))
  }

  def themeUpdate(id: String): Result = Template('id -> Option(id))

  def themeSubmit(data: String): Result = {
    val theme = JJson.parse[ThemeViewModel](data)

    val persistedTheme = theme.id match {
      case None => {
        val inserted = PortalTheme.insert(PortalTheme(
          name = theme.name,
          localiseQueryKeys = theme.localisedQueryKeys,
          hiddenQueryFilter = theme.hiddenQueryFilter,
          subdomain = theme.subdomain,
          defaultLanguage = theme.defaultLanguage,
          solrSelectUrl = theme.solrSelectUrl, 
          cacheUrl = theme.cacheUrl,
          emailTarget = theme.emailTarget,
          homePage = theme.homePage)
        )
        inserted match {
          case Some(id) => Some(theme.copy(id = inserted))
          case None => None
        }
      }
      case Some(oid) => {
        val existing = PortalTheme.findOneByID(oid)
        if(existing == None) return NotFound(&("admin.themes.themeNotFound", oid))
        val updated = existing.get.copy(
          name = theme.name,
          localiseQueryKeys = theme.localisedQueryKeys,
          hiddenQueryFilter = theme.hiddenQueryFilter,
          subdomain = theme.subdomain,
          defaultLanguage = theme.defaultLanguage,
          solrSelectUrl = theme.solrSelectUrl,
          cacheUrl = theme.cacheUrl,
          emailTarget = theme.emailTarget,
          homePage = theme.homePage)
        PortalTheme.save(updated)
        Some(theme)
      }
    }

    persistedTheme match {
      case Some(theTheme) => {
        ThemeHandler.update()
        Json(theTheme)
      }
      case None => Error(&("admin.themes.saveError", theme.name))
    }

  }

  def reload: Result = {
    info("Reloading entire configuration from disk.")
    val themeList = ThemeHandler.readThemesFromDisk()
    themeList foreach {
      PortalTheme.insert(_)
    }
    ThemeHandler.update()
    Text("Themes reloaded")
  }

}

case class ThemeViewModel(id: Option[ObjectId] = None,
                          name: String = "",
                          localisedQueryKeys: List[String] = List(),
                          possibleQueryKeys: List[String] = List(),
                          hiddenQueryFilter: Option[String] = Some(""),
                          subdomain: Option[String] = None,
                          defaultLanguage: String = "",
                          solrSelectUrl: String = "http://localhost:8983/solr",
                          cacheUrl: String = "http://localhost:8983/services/image?",
                          emailTarget: EmailTarget = EmailTarget(),
                          homePage: Option[String] = None,
                          metadataPrefix: Option[String] = Some("icn"),
                          errors: Map[String, String] = Map.empty[String, String]) extends ViewModel