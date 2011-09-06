package controllers

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Labels extends DelvingController {

  import views.Label._
  
  def list(user: String): AnyRef = {
    val u = getUser(user)
    html.list(user = u)
  }

  def label(user: String, label: String): AnyRef = {
    val u = getUser(user)
    html.label(user = u, name = label)

  }

}