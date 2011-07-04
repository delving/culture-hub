package controllers

import play.mvc.{Controller}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Users extends DelvingController {

  import views.User._

  def index(user: String) = {
    val u = getUser(user)
    html.index(username = u.displayName)
  }

}