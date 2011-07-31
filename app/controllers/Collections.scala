package controllers

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController {

  import views.Collection._
  
  def list(user: String): AnyRef = {
    val u = getUser(user)
    html.list(user = u)
  }

  def collection(user: String, collection: String): AnyRef = {
    val u = getUser(user)
    html.collection(user = u, name = collection)

  }

  def add(user: String): AnyRef = {
    val u = getUser(user)
    html.add(user = u)
  }
}