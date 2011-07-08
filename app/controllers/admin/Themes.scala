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
    val hasSuccess = ComponentRegistry.themeHandler.readThemesFromDatabase()
    // TODO more detailed error reporting with cause etc.
    if(!hasSuccess) flash += ("error" -> "Error reloading themes. Make sure your theme file is OK")
    Action(index())
  }

}