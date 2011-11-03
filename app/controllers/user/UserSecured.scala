package controllers.user

import controllers.{Secure, DelvingController}
import play.mvc.Before
import play.mvc.results.Result

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait UserSecured extends Secure { self: DelvingController =>

  @Before def checkUser(): Result = {
    if (connectedUser != params.get("user")) {
      return Forbidden(&("user.secured.noAccess"))
    }
    Continue
  }

}