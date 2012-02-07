package controllers.organization

import controllers.OrganizationController
import play.api.mvc.Results._
import models.Organization
import extensions.JJson
import play.api.i18n.Messages
import play.api.mvc.Action

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends OrganizationController {

  def index(implicit orgId: String) = OrgOwnerAction {
    Action {
      implicit request =>
        val org = Organization.findByOrgId(orgId)
        if (org.isEmpty) {
          NotFound(Messages("organizations.organization.orgNotFound").format(orgId))
        } else {
          val membersAsTokens = JJson.generate(org.get.users.map(m => Map("id" -> m, "name" -> m)))
          val idAndOwners = Organization.listOwnersAndId(orgId)
          Ok(Template('members -> membersAsTokens, 'owners -> idAndOwners._2, 'ownerGroupId -> idAndOwners._1.getOrElse("")))
        }
    }

  }

}
