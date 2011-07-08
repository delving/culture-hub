package controllers.admin

import controllers.DelvingController
import cake.ComponentRegistry
import models.PortalTheme

/**
 * TODO add Access Control
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Themes extends DelvingController {

  import views.Admin.Themes._

  def index(): AnyRef = {
    val themeList = PortalTheme.findAll
    html.index(themes = themeList)
  }

  def reload(): AnyRef = {
    try {
      ComponentRegistry.themeHandler.readThemesFromDatabase()
    } catch {
      // TODO more detailed error reporting with cause etc.
      case _ => flash += ("error" -> "Error reloading themes. Make sure your theme file is OK")
    }
    Action(index())
  }

  def list(): AnyRef = {
    import net.liftweb.json._

    val themeList = PortalTheme.findAll
    Json(Map("themes" -> themeList))
  }

  def update() {
    val theme = params.get("theme", classOf[PortalTheme])
    Ok
  }

}