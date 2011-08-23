package views {

import play.data.validation.Validation
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.templates.JavaExtensions
import models.User

package object context {

  def flash = play.mvc.Scope.Flash.current()
  def params = play.mvc.Scope.Params.current()
  def renderArgs = play.mvc.Scope.RenderArgs.current()
  def validation = Validation.current()
  def errors = validation.errorsMap()
  def showError(key: String) = Validation.error(key)

  def userName = renderArgs.get("displayName")
  def fullName = renderArgs.get("fullName")

  def browsedUserName = renderArgs.get("browsedDisplayName")
  def browsedFullName = renderArgs.get("browsedFullName")

  def niceTime(timestamp: Long) = new DateTime(timestamp).toString(DateTimeFormat.fullDateTime())
  def niceText(text: String) = JavaExtensions.nl2br(text)

  implicit def userListToString(users: List[User]): String = (for(u <- users) yield u.fullname) reduceLeft (_ + ", " + _)

}

}
