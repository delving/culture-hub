package controllers.organization

import controllers.OrganizationController
import extensions.JJson
import play.api.i18n.Messages
import play.api.mvc.Action
import util.ThemeHandler
import models.{PortalTheme, User, Organization}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends OrganizationController {

  def index(orgId: String) = OrgOwnerAction(orgId) {
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

  /**
   * Add to organization
   */
  def addUser(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").get
        User.findByUsername(id).getOrElse(Error(Messages("organizations.admin.userNotFound", id)))
        val success = Organization.addUser(orgId, id)
        // TODO logging
        if (success) Ok else Error
    }
  }

  /**
   * Remove from organization
   */
  def removeUser(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").get
        User.findByUsername(id).getOrElse(Error(Messages("organizations.admin.userNotFound", id)))
        val success = Organization.removeUser(orgId, id)
        // TODO logging
        if (success) Ok else Error
    }
  }

  def reloadThemes(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        info("Reloading entire configuration from disk.")
        val themeList = ThemeHandler.readThemesFromDisk
        themeList foreach {
          PortalTheme.insert(_)
        }
        ThemeHandler.update()
        Ok("Themes reloaded")
    }
  }

}