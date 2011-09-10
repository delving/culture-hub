package views {

import play.data.validation.Validation
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.templates.JavaExtensions
import play.mvc.Http
import models.PortalTheme

package object context {

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

  // ~~~ template helpers
  def niceTime(timestamp: Long) = new DateTime(timestamp).toString(DateTimeFormat.fullDateTime())
  def niceTime(timestamp: DateTime) = timestamp.toString(DateTimeFormat.fullDateTime())
  def niceText(text: String) = JavaExtensions.nl2br(text)
  def isCurrent(controller: String) = Http.Request.current().controller == controller

  implicit def userListToString(users: List[models.User]): String = (for(u <- users) yield u.fullname) reduceLeft (_ + ", " + _)

  // ~~~ themes
  def theme = renderArgs.get("theme").asInstanceOf[PortalTheme]

  def themeName = theme.name
  def themeTemplateDir = theme.templateDir
  def themeDisplayName = theme.displayName

}

}
