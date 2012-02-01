package core

import models.PortalTheme
import util.ThemeInfoReader
import play.api.PlayException

/**
 * Provides theme-related configuration and information to the view
 */
class ThemeInfo(theme: PortalTheme) {

  def get(property: String) = {
    themeProperty[String](property, classOf[String])
  }

  def themeProperty[T](property: String, clazz: Class[T] = classOf[String])(implicit mf: Manifest[T]): T = {
    val value: String = ThemeInfoReader.get(property, theme.name) match {
      case Some(prop) => prop
      case None =>
        ThemeInfoReader.get(property, "default") match {
          case Some(prop) => prop
          case None => throw PlayException("Programmer Exceptions", "No default value, nor actual value, defined for property '%s' in application.conf".format(property))
        }
    }

    val INT = classOf[Int]
    val result = mf.erasure match {
      case INT => Integer.parseInt(value)
      case _ => value
    }

    result.asInstanceOf[T]
  }

  def path(path: String) = "/assets/themes/%s/%s".format(theme.name, path)

  val displayName = themeProperty("displayName")

}