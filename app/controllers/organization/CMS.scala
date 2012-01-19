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

package controllers.organization

import com.mongodb.casbah.Imports._
import play.mvc.Before
import play.mvc.results.Result
import extensions.JJson
import play.data.validation.Annotations._
import play.Play
import controllers.{ViewModel, DelvingController}
import models._
import play.data.validation.Validation
import play.i18n.Lang

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object CMS extends DelvingController with OrganizationSecured {

  val MAIN_MENU = "mainMenu"
  
  implicit def cmsPageToViewModel(p: CMSPage) = {
    // for the moment we have one main menu so we can do it like this
    val menuEntryPosition = MenuEntry.findByPageAndMenu(p.orgId, MAIN_MENU, p.key) match {
      case Some(e) => e.position
      case None => MenuEntry.findEntries(p.orgId, p.theme, MAIN_MENU).length + 1
    }
    CMSPageViewModel(p._id.getTime, p.key, p.theme, p.lang, p.title, p.content, p.isSnippet, menuEntryPosition)
  }
  implicit def cmsPageListToViewModelList(l: List[CMSPage]) = l.map(cmsPageToViewModel(_))

  @Before
  def checkAccess(orgId: String): Result = {
    if(!Organization.isOwner(orgId, connectedUser) && Group.count(MongoDBObject("users" -> connectedUser, "grantType.value" -> GrantType.CMS.value)) == 0) {
      return Forbidden(&("user.secured.noAccess"))
    }
    Continue
  }

  def list(orgId: String, language: String): Result = {
    val lang = if(language == null) Lang.get() else language // broken Play binder.......
    val pages = CMSPage.list(orgId, lang)
    Template('data -> JJson.generate(Map("pages" -> pages)), 'languages -> getLanguages, 'currentLanguage -> lang)
  }
  
  def page(orgId: String, language: String, page: Option[String]): Result = {

    def menuEntries = MenuEntry.findEntries(orgId, theme.name, MAIN_MENU)

    val p: (CMSPageViewModel, List[CMSPageViewModel]) = page match {
      case None => (CMSPageViewModel(System.currentTimeMillis(), "", theme.name, language, "", "", false, menuEntries.length + 1), List.empty)
      case Some(key) =>
        val versions = CMSPage.findByKey(orgId, key)
        if(versions.length == 0) return NotFound
        (versions.head, versions)

    }

    Template('page -> JJson.generate(p._1), 'versions -> JJson.generate(Map("versions" -> p._2)), 'languages -> getLanguages, 'themes -> getThemes, 'isNew -> (p._2.size == 0))
  }
  
  def pageSubmit(orgId: String): Result = {
    val pageModel = JJson.parse[CMSPageViewModel](params.get("data"))
    if("^[a-z0-9]{3,15}$".r.findFirstIn(pageModel.key) == None) {
      Validation.addError("page.key", "org.cms.page.keyInvalid", pageModel.key)
    }
    validate(pageModel).foreach { errors => return JsonBadRequest(pageModel.copy(errors = errors)) }

    // create / update the entry before we create / update the page since in the implicit conversion above we'll query for that page's position.

    MenuEntry.addPage(orgId, theme.name, MAIN_MENU, pageModel.key, pageModel.position, pageModel.title, pageModel.lang)
    val page: CMSPageViewModel = CMSPage.create(orgId, theme.name, pageModel.key, pageModel.lang, connectedUser, pageModel.title, pageModel.content)
    
    Json(page)
  }
  
  def pageDelete(orgId: String, key: String, language: String): Result = {
    CMSPage.delete(orgId, key, language)
    Ok
  }

  private def getThemes = {
    if(Play.mode.isDev || PortalTheme.findAll.length == 1) {
      PortalTheme.findAll.map(t => (t.name, t.name))
    } else {
      PortalTheme.findAll.filterNot(_.name == "default").map(t => (t.name, t.name))
    }
  }
  
  private def getLanguages = Play.configuration.getProperty("application.langs").split(",").map(l => (l.trim, &("locale." + l.trim)))
  
}

case class CMSPageViewModel(dateCreated: Long,
                            @Required key: String,  // the key of this page (unique across all version sets of pages)
                            @Required theme: String, // the hub theme this page belongs to
                            @Required lang: String, // 2-letters ISO code of the page language
                            @Required title: String, // title of the page in this language
                            content: String, // actual page content (text)
                            isSnippet: Boolean = false, // is this a snippet in the welcome page or not
                            position: Int,
                            errors: Map[String, String] = Map.empty[String, String]) extends ViewModel