package views {

import play.data.validation.Validation
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.templates.JavaExtensions
import org.bson.types.ObjectId
import models.{PortalTheme}
import play.mvc.Http
import controllers.FileStore
import util.Implicits

package object context extends Implicits {

  val PAGE_SIZE = 4

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
