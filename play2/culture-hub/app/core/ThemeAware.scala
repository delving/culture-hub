package core

import models.PortalTheme
import play.api.mvc._
import util.ThemeHandler
import play.templates.groovy.GroovyTemplates

trait ThemeAware {
  self: Controller with GroovyTemplates =>

  private val themeThreadLocal: ThreadLocal[PortalTheme] = new ThreadLocal[PortalTheme]

  implicit def theme = themeThreadLocal.get()

  def Themed[A](action: Action[A]): Action[A] = {
    Action(action.parser) {
      request =>
        try {
          val portalTheme = ThemeHandler.getByDomain(request.domain)
          themeThreadLocal.set(portalTheme)
          renderArgs += ("themeInfo" -> new ThemeInfo(portalTheme))
          action(request)
        } catch {
          case t => throw t
        } finally {
          themeThreadLocal.remove()

        }
    }
  }

}