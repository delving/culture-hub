package controllers.user

import play.templates.Html
import play.mvc.Before
import controllers.{UserAuthentication, Secure, DelvingController}
import play.mvc.results.Result
import models.UserGroup

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends DelvingController with UserAuthentication with Secure {

  import views.User.Admin._

  @Before def checkUser(): Result = {
    if (connectedUser == null || connectedUser != params.get("user")) {
      return Forbidden("You do not have access here")
    }
    Continue
  }


  def index: Html = {
    html.index()
  }

  def groupList: Html = {
    val userGroups = UserGroup.findByUser(getUserId(connectedUser))
    html.groupList(groups = userGroups)
  }

  def groupNew: Html = {
    html.groupNew()
  }

}