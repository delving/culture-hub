package controllers.organization

import controllers.DelvingController
import play.mvc.results.Result
import play.mvc.Before
import extensions.JJson
import models.{User, Organization}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends DelvingController with OrganizationSecured {

  @Before() def setOrgId() {
    val orgId = params.get("orgId")
    renderArgs += ("orgId" -> orgId)
  }

  @Before(priority = 2) def checkOwner(): Result = {
    val orgId = params.get("orgId")
    if(orgId == null || orgId.isEmpty) Error("How did you even get here?")
    if(!Organization.isOwner(connectedUserId)) return Forbidden(&("user.secured.noAccess"))
    Continue
  }

  def index(orgId: String): Result = {
    val org = Organization.findByOrgId(orgId).getOrElse(return NotFound("Could not find organization " + orgId))
    val membersAsTokens = JJson.generate(org.users.map(m => Map("id" -> m, "name" -> m)))
    Template('members -> membersAsTokens)
  }

  def addUser(orgId: String, userName: String): Result = {
    User.findByUsername(userName).getOrElse(return Error(&("organizations.admin.userNotFound", userName)))
    val success = Organization.addUser(orgId, userName)
    if(success) {
      Ok
    } else {
      Error
    }
  }

  def removeUser(orgId: String, userName: String): Result = {
    Ok
  }

}