package controllers.admin

import controllers.DelvingController
import cake.ComponentRegistry

/**
 * TODO add Access Control
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Themes extends DelvingController {

  import views.admin.Themes._

  def index(): AnyRef = {
    html.index()
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

}