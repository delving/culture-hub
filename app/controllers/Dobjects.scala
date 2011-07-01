package controllers

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object Dobjects extends DelvingController {

  import views.Dobject._
  
  def list(user: String): AnyRef = {
    val u = getUser(user)
    html.list(user = u)
  }

  def dobject(user: String, dobject: String): AnyRef = {
    val u = getUser(user)
    html.dobject(user = u, name = dobject)
  }

}