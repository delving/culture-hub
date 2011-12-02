package controllers.admin

import play.mvc.Before
import play.mvc.results.Result
import models.User
import controllers.{Secure, DelvingController}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait AdminSecure extends Secure { self: DelvingController =>

  @Before
  def checkAdmin(): Result = {
    val u = User.findByUsername(connectedUser) getOrElse(return Forbidden("Wrong user"))
    if(!u.isHubAdmin.getOrElse(false)) {
      reportSecurity("User %s tried to get access to themes admin".format(connectedUser))
      return Forbidden(&("user.secured.noAccess"))
    }
    Continue
  }



}