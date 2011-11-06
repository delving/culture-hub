package controllers.organization

import play.mvc.Before
import play.mvc.results.Result
import controllers.{AccessControl, DelvingController, Secure}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait OrganizationSecured extends Secure { self: DelvingController =>

  @Before def checkUser(): Result = {
    val orgId = params.get("orgId")
    if(orgId == null || orgId.isEmpty) Error("How did you even get here?")
    val organizations = session.get(AccessControl.ORGANIZATIONS)
    if(organizations == null || organizations.isEmpty) return Forbidden(&("user.secured.noAccess"))
    if(!organizations.split(",").contains(orgId)) return Forbidden(&("user.secured.noAccess"))
    Continue
  }

}