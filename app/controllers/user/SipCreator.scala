package controllers.user

import controllers.DelvingController
import play.templates.Html
import play.mvc.results.Result
import views.User.SipCreator._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object SipCreator extends DelvingController with UserSecured {

  def index: Html = html.index()

  def jnlp: Result = {

    response.contentType = "application/jnlp" // TODO correct content-type

    val jnlp =
    <jnlp>
      <user>{connectedUser}</user>
    </jnlp>

    Xml(jnlp)
  }
}