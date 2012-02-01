package core

import models.PortalTheme
import play.api.mvc._
import util.ThemeHandler

trait ThemeAware {
  self: Controller =>

  private val themeThreadLocal: ThreadLocal[PortalTheme] = new ThreadLocal[PortalTheme]

  implicit def theme = themeThreadLocal.get()

  def Themed[A](action: Action[A]): Action[A] = {
    Action(action.parser) {
      request =>
        try {
          val portalTheme = ThemeHandler.getByDomain(request.domain)
          themeThreadLocal.set(portalTheme)
          action(request)
        } catch {
          case t => throw t
        } finally {
          themeThreadLocal.remove()

        }
    }
  }

}