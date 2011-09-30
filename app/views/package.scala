package views {

import play.data.validation.Validation
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.templates.JavaExtensions
import org.bson.types.ObjectId
import play.mvc.Http
import util.Implicits
import controllers.{ViewModel, FileStore}
import models.{UserCollection, DObject, PortalTheme}

package object context extends Implicits {

  val PAGE_SIZE = 16

  // ~~~ play variables
  def flash = play.mvc.Scope.Flash.current()
  def params = play.mvc.Scope.Params.current()
  def renderArgs = play.mvc.Scope.RenderArgs.current()
  def validation = Validation.current()
  def request = Http.Request.current()
  def errors = validation.errorsMap()
  def showError(key: String) = Validation.error(key)

  // ~~~ connected user
  def userName = renderArgs.get("displayName")
  def fullName = renderArgs.get("fullName")

  // ~~~ browsed user
  def browsedUserName = renderArgs.get("browsedDisplayName")
  def browsedFullName = renderArgs.get("browsedFullName")

  def connectedIsBrowsed = userName == browsedUserName

  // ~~~ url building
  def paginationUrl: String = {
    val query = Option(params.get("query")) getOrElse ""
    request.path + "?query=%s&page=".format(query)
  }

  def thumbnailUrl(thumbnail: ObjectId) = "/thumbnail/%s".format(thumbnail)
  
  def thumbnailUrl(thumbnail: Option[ObjectId]) = thumbnail match {
    case Some(t) => "/thumbnail/%s".format(t)
    case None => "/public/images/dummy-object.png" // TODO now that's not very clean, is it?
  }

  def imageUrl(image: ObjectId) = if(FileStore.imageExists(image)) "/file/image/" + image else "/public/images/dummy-object.png" // TODO now that's not very clean, is it?

  def searchUrl = request.path

  // ~~~ template helpers
  def niceTime(timestamp: Long) = new DateTime(timestamp).toString(DateTimeFormat.fullDateTime())
  def niceTime(timestamp: DateTime) = timestamp.toString(DateTimeFormat.fullDateTime())
  def niceText(text: String) = JavaExtensions.nl2br(text)

  def isCurrent(controller: String) = Http.Request.current().controller == controller

  implicit def userListToString(users: List[models.User]): String = (for(u <- users) yield u.fullname) reduceLeft (_ + ", " + _)

  def printValidationRules(name: String)(implicit viewModel: Option[Class[_ <: ViewModel]]) = viewModel match {
    case Some(c) => util.Validation.getClientSideValidationRules(c)(name)
    case None => ""
  }

  def crumble: List[(String, String)] = {
    val crumbList = request.path.split("/").drop(1).toList
    val crumbs = crumbList match {

      case "users" :: Nil => List(("/users", "Users"))
      case "objects" :: Nil => List(("/objects", "Objects"))
      case "collections" :: Nil => List(("/collections", "Collections"))
      case "stories" :: Nil => List(("/stories", "Stories"))

      case user :: Nil => List(("/" + user, user))

      case user :: "object" :: "add" :: Nil => List(("/" + user, user), ("/" + user + "/object", "Objects"), ("/" + user + "/object/add", "Create new object"))
      case user :: "collection" :: "add" :: Nil => List(("/" + user, user), ("/" + user + "/collection", "Collections"), ("/" + user + "/collection/add", "Create new collection"))
      case user :: "story" :: "add" :: Nil => List(("/" + user, user), ("/" + user + "/story", "Stories"), ("/" + user + "/story/add", "Create new story"))

      case user :: "object" :: id :: Nil => List(("/" + user, user), ("/" + user + "/object", "Objects"), ("/" + user + "/object/" + id, DObject.fetchName(id)))
      case user :: "collection" :: id :: Nil => List(("/" + user, user), ("/" + user + "/collection", "Collections"), ("/" + user + "/collection/" + id, UserCollection.fetchName(id)))
      case user :: "story" :: id :: Nil => List(("/" + user, user), ("/" + user + "/story", "Stories"), ("/" + user + "/story/" + id, models.Story.fetchName(id)))

      case user :: "object" :: id :: "update" :: Nil => List(("/" + user, user), ("/" + user + "/object", "Objects"), ("/" + user + "/object/" + id, "Update object \"" + DObject.fetchName(id) + "\""))
      case user :: "collection" :: id :: "update" :: Nil => List(("/" + user, user), ("/" + user + "/collection", "Collections"), ("/" + user + "/collection/" + id, "Update collection \"" + UserCollection.fetchName(id)  + "\"" ))
      case user :: "story" :: id :: "update" :: Nil => List(("/" + user, user), ("/" + user + "/story", "Stories"), ("/" + user + "/story/" + id, "Update story \"" + models.Story.fetchName(id) + "\""))

      case user :: "collection" :: cid :: "object" :: oid ::Nil => List(("/" + user, user), ("/" + user + "/collection", "Collections"), ("/" + user + "/collection/" + cid, UserCollection.fetchName(cid)), ("/" + user + "/collection/" + cid + "/object/" + oid, DObject.fetchName(oid)))

      case _ => List()
    }
    ("/", "Home") :: crumbs
  }



  // ~~~ themes
  def theme = renderArgs.get("theme").asInstanceOf[PortalTheme]

  def themeName = theme.name
  def themeTemplateDir = theme.templateDir
  def themeDisplayName = theme.displayName
  def themeText = theme.text

  def themePath(path: String) = "/public/themes/%s/%s".format(themeName, path)

  // ~~~ temporary helper, should be replaced with cache
  def fullName(userName: String) = models.User.findByUsername(userName, "cultureHub").get.fullname

}

}
