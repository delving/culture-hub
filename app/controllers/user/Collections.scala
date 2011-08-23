package controllers.user

import controllers.{Secure, UserAuthentication, DelvingController}
import play.templates.Html
import views.User.Collection._

/**
 * Manipulation of user collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController with UserAuthentication with Secure {

  def collectionUpdate(id: String): Html = html.add(Option(id))

}