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

package views {

import play.data.validation.Validation
import play.templates.JavaExtensions
import org.bson.types.ObjectId
import models.{UserCollection, DObject, PortalTheme}
import java.util.Date
import java.text.SimpleDateFormat
import controllers.dos.ImageDisplay
import play.mvc.Http
import controllers.{ViewUtils, Internationalization, ViewModel}
import play.i18n.Lang

package object context extends Internationalization {

  val DEFAULT_THUMBNAIL = "/public/images/dummy-object.png"
  val PAGE_SIZE = 12

  // ~~~ play variables
  def flash = play.mvc.Scope.Flash.current()
  def params = play.mvc.Scope.Params.current()
  def renderArgs = play.mvc.Scope.RenderArgs.current()
  def validation = Validation.current()
  def request = Http.Request.current()
  def errors = validation.errorsMap()
  def showError(key: String) = Validation.error(key)

  // ~~~ connected user
  def userName = renderArgs.get("userName")
  def fullName = renderArgs.get("fullName")

  // ~~~ browsed user
  def browsedUserName = renderArgs.get("browsedUserName")
  def browsedFullName = renderArgs.get("browsedFullName")

  def connectedIsBrowsed = userName == browsedUserName

  // ~~~ url building
  def paginationUrl: String = {
    val query = Option(params.get("query")) getOrElse ""
    request.path + "?query=%s&page=".format(query)
  }

  def getThumbnailUrl(thumbnail: Option[ObjectId], size: Int = 100) = thumbnailUrl(thumbnail, size)

  def thumbnailUrl(thumbnail: Option[ObjectId], size: Int = 100) = thumbnail match {
    case Some(t) => "/thumbnail/%s/%s".format(t, size)
    case None => DEFAULT_THUMBNAIL // TODO now that's not very clean, is it?
  }

  def imageUrl(image: ObjectId) = if(ImageDisplay.imageExists(image)) "/image/" + image else "/public/images/dummy-object.png" // TODO now that's not very clean, is it?

  def searchUrl = request.path

  // ~~~ template helpers
  val niceTimeFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm")
  def niceTime(timestamp: Long) = niceTimeFormat.format(new Date(timestamp))
  def niceTime(timestamp: Date) = niceTimeFormat.format(timestamp)
  def niceText(text: String) = JavaExtensions.nl2br(text)

  def isCurrent(action: String) = Http.Request.current().action.startsWith(action)

  implicit def userListToString(users: List[models.User]): String = (for(u <- users) yield u.fullname) reduceLeft (_ + ", " + _)

  def printValidationRules(name: String) = Option(renderArgs.get("viewModel")) match {
    case Some(c) => {
      val vm = c.asInstanceOf[Class[_ <: ViewModel]]
      val rules = util.Validation.getClientSideValidationRules(vm)
      if(rules.get(name) == None) throw new util.ProgrammerException("Unknown field '%s' for view model %s".format(name, vm.getName)) else rules(name)
    }
    case None => ""
  }

  /**
  * Breadcrumb computation based on URL. Context data is passed in through a map of maps, the inner map containing (url, label)
  */
  def crumble(p: java.util.Map[String, java.util.Map[String, String]]): List[((String, String), Int)] = {
    val session = play.mvc.Scope.Session.current()

    // we can't make the difference between orgId/object and user/object
    val crumbList = if(session.get(controllers.Search.SEARCH_TERM) != null && request.headers.get("referer") != null && request.headers.get("referer").value().contains("search") ) {
      "org" :: request.path.split("/").drop(1).toList
    } else {
      request.path.split("/").drop(1).toList
    }
    val crumbs = crumbList match {

      case "users" :: Nil => List(("/users", &("thing.users")))
      case "objects" :: Nil => List(("/objects", &("thing.objects.user")))
      case "heritageObjects" :: Nil => List(("/objects", &("thing.objects.heritage")))
      case "collections" :: Nil => List(("/collections", &("thing.collection")))
      case "stories" :: Nil => List(("/stories", &("thing.stories")))

      case "org" :: "search" :: Nil =>
        val queryString = session.get(controllers.Search.RETURN_TO_RESULTS)
        val searchTerm = "[%s]".format(session.get(controllers.Search.SEARCH_TERM))
        List(("NOLINK", &("ui.label.search")), ("/search?" + queryString , searchTerm))

      case "org" :: orgId :: "object" :: spec :: recordId :: Nil =>
        Option(session.get(controllers.Search.RETURN_TO_RESULTS)) match {
          case Some(r) =>
            val searchTerm = "[%s]".format(session.get(controllers.Search.SEARCH_TERM))
            List(("NOLINK", &("ui.label.search")), ("/search?" + r, searchTerm), ("NOLINK", p.get("title").get("label")))
          case None =>
            List(("/organizations/" + orgId, orgId), ("NOLINK", &("thing.objects")), ("NOLINK", spec), ("NOLINK", p.get("title").get("label")))
        }

      case "organizations" :: orgName :: Nil => List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName))
      case "organizations" :: orgName :: "admin" :: Nil => List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/admin", &("org.admin.index.title")))
      case "organizations" :: orgName :: "dataset" :: Nil => List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/dataset", &("thing.datasets")))
      case "organizations" :: orgName :: "dataset" :: name :: Nil => List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/dataset", &("thing.datasets")), ("/organizations/" + orgName + "/dataset" + name, name))
      case "organizations" :: orgName :: "dataset" :: name :: "update" ::  Nil => List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/dataset", &("thing.datasets")), ("/organizations/" + orgName + "/dataset/" + name, name), ("/organizations/" + orgName + "/dataset/" + name + "/update", &("ui.label.edit")))
      case "organizations" :: orgName :: "groups" ::  Nil => List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/groups", &("thing.groups")))
      case "organizations" :: orgName :: "groups" ::  "create" :: Nil => List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/groups", &("thing.groups")), ("NOLINK", &("ui.label.create")))
      case "organizations" :: orgName :: "groups" ::  "update" :: id :: Nil => List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/groups", &("thing.groups")), ("/organizations/" + orgName + "/groups/update/" + id, &("ui.label.edit")))
      case "organizations" :: orgName :: "sip-creator" :: Nil => List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/sip-creator", &("ui.label.sipcreator")))
      case "organizations" :: orgName :: "site" :: Nil =>  List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", &("org.cms")), ("NOLINK", &("locale." + Lang.get())))
      case "organizations" :: orgName :: "site" :: lang :: Nil =>  List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", &("org.cms")), ("NOLINK", &("locale." + lang)))
      case "organizations" :: orgName :: "site" :: lang :: "page" :: "add" :: Nil => List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", &("org.cms")), ("/organizations/" + orgName + "/site/" + lang, &("locale." + lang)), ("NOLINK", &("org.cms.page.create")))
      case "organizations" :: orgName :: "site" :: lang :: "page" :: page :: "update" :: Nil => List(("NOLINK", &("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", &("org.cms")), ("/organizations/" + orgName + "/site/" + lang, &("locale." + lang)), ("NOLINK", &("org.cms.page.update") + " \"" + page + "\""))

      case user :: Nil => List(("/" + user, user))
      case user :: "collection" :: Nil => List(("/" + user, user), ("/" + user + "/collection", &("thing.collections")))
      case user :: "object" :: Nil => List(("/" + user, user), ("/" + user + "/object", &("thing.objects")))
      case user :: "dataset" :: Nil => List(("/" + user, user), ("/" + user + "/dataset", &("thing.datasets")))
      case user :: "story" :: Nil => List(("/" + user, user), ("/" + user + "/story", &("thing.stories")))

      case user :: "object" :: "add" :: Nil => List(("/" + user, user), ("/" + user + "/object", &("thing.objects")), ("/" + user + "/object/add", &("user.object.create")))
      case user :: "collection" :: "add" :: Nil => List(("/" + user, user), ("/" + user + "/collection", &("thing.collections")), ("/" + user + "/collection/add", &("user.collection.create")))
      case user :: "story" :: "add" :: Nil => List(("/" + user, user), ("/" + user + "/story", &("thing.stories")), ("/" + user + "/story/add", &("user.story.create")))

      case user :: "object" :: id :: Nil => List(("/" + user, user), ("/" + user + "/object", &("thing.objects")), ("/" + user + "/object/" + id, DObject.fetchName(id)))
      case user :: "collection" :: id :: Nil => List(("/" + user, user), ("/" + user + "/collection", &("thing.collections")), ("/" + user + "/collection/" + id, UserCollection.fetchName(id)))
      case user :: "story" :: id :: Nil => List(("/" + user, user), ("/" + user + "/story", &("thing.stories")), ("/" + user + "/story/" + id, models.Story.fetchName(id)))
      case user :: "story" :: id :: "read" :: Nil => List(("/" + user, user), ("/" + user + "/story", &("thing.stories")), ("/" + user + "/story/" + id, models.Story.fetchName(id)), ("/" + user + "/story/" + id, &("thing.story")))

      case user :: "object" :: id :: "update" :: Nil => List(("/" + user, user), ("/" + user + "/object", &("thing.objects")), ("/" + user + "/object/" + id,  DObject.fetchName(id)),("/" + user + "/object/" + id, &("user.object.updateObject", DObject.fetchName(id))))
      case user :: "collection" :: id :: "update" :: Nil => List(("/" + user, user), ("/" + user + "/collection", &("thing.collections")), ("/" + user + "/collection/" + id, &("user.collection.update", UserCollection.fetchName(id))))
      case user :: "story" :: id :: "update" :: Nil => List(("/" + user, user), ("/" + user + "/story", &("thing.stories")), ("/" + user + "/story/" + id, &("user.story.updateStory", models.Story.fetchName(id))))

      case user :: "collection" :: cid :: "object" :: oid ::Nil => List(("/" + user, user), ("/" + user + "/collection", &("thing.collections")), ("/" + user + "/collection/" + cid, UserCollection.fetchName(cid)), ("/" + user + "/collection/" + cid + "/object/" + oid, DObject.fetchName(oid)))

      case _ => List()
    }
    (("/", "Home") :: crumbs).zipWithIndex
  }



  // ~~~ themes
  def theme = renderArgs.get("theme").asInstanceOf[PortalTheme]

  def viewUtils: ViewUtils = renderArgs.get("viewUtils").asInstanceOf[ViewUtils]
  def themeName = theme.name
  def themeDisplayName = viewUtils.themeProperty("displayName")
  def themeText = viewUtils.themeProperty("text")

  def themePath(path: String) = "/public/themes/%s/%s".format(themeName, path)

  // ~~~ temporary helper, should be replaced with cache
  def fullName(userName: String) = models.User.findByUsername(userName).get.fullname

}

}
