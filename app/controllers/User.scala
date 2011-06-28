package controllers

import play.mvc.{Controller}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object User extends Controller with Secure {

  import views.User._

  def index = {
    html.index(username = "Melvin")
  }

}