package controllers

import play.mvc.Controller

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Application extends Controller {

  import views.Application._

  def index = {
    html.index(title = "Howdy!")
  }

}