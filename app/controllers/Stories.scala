package controllers

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

object Stories extends DelvingController {

  import views.Story._

  def list(user: String): AnyRef = {
    val u = getUser(user)
    html.list(user = u)
  }

  def story(user: String, story: String): AnyRef = {
    val u = getUser(user)
    html.story(user = u, name = story)
  }

}