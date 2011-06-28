package controllers

import play.mvc.{Controller}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Profile extends Controller {

  import views.Profile._

  def index = {
    html.index()
  }
}
