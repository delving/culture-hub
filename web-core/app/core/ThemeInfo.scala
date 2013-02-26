package core

import models.OrganizationConfiguration
import util.ThemeInfoReader
import play.api.PlayException

/**
 * Provides graphical theme related configuration and information to the view
 */
class ThemeInfo(configuration: OrganizationConfiguration) {

  def getConfiguration = configuration

  def get(property: String) = {
    themeProperty[String](property, classOf[String])
  }

  def themeProperty[T](property: String, clazz: Class[T] = classOf[String])(implicit mf: Manifest[T]): T = {
    val value: String = ThemeInfoReader.get(property, configuration.orgId, configuration.ui.themeDir) match {
      case Some(prop) => prop
      case None =>
        ThemeInfoReader.get(property, "default", "default") match {
          case Some(prop) => prop
          case None => throw new PlayException("Programmer Exceptions", "No default value, nor actual value, defined for property '%s' in application.conf".format(property))
        }
    }

    val INT = classOf[Int]
    val result = mf.runtimeClass match {
      case INT => Integer.parseInt(value)
      case _ => value
    }

    result.asInstanceOf[T]
  }

  def path(path: String) = "/assets/themes/%s/%s".format(configuration.ui.themeDir, path)

  val siteName = configuration.ui.siteName.getOrElse("Delving CultureHub")
  val siteSlogan = configuration.ui.siteSlogan.getOrElse("")
  val footer = configuration.ui.footer.getOrElse("")
  val addThisTrackingCode = configuration.ui.addThisTrackingCode.getOrElse("")
  val googleAnalyticsTrackingCode = configuration.ui.googleAnalyticsTrackingCode.getOrElse("")
  val showLogin = configuration.ui.showLogin
  val showRegistration = configuration.ui.showRegistration
  val showAllObjects = configuration.ui.showAllObjects

  val pageSize = configuration.searchService.pageSize
}