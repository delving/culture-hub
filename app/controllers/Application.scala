package controllers

import play.mvc.Controller

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Application extends Controller with Secure {

  import views.Application._

  def index = {
    html.index(title = "Howdy!", username = connectedUser)
  }

}
