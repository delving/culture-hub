package controllers.organization

import controllers.OrganizationController
import extensions.JJson
import play.api.i18n.Messages
import play.api.mvc.Action
import util.ThemeHandler
import models._
import core.HubServices

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends OrganizationController {

  def index(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        if (!HubServices.organizationService.exists(orgId)) {
          NotFound(Messages("organizations.organization.orgNotFound").format(orgId))
        } else {
          val membersAsTokens = JJson.generate(HubUser.listOrganizationMembers(orgId).map(m => Map("id" -> m, "name" -> m)))
          Ok(Template('members -> membersAsTokens, 'owners -> HubServices.organizationService.listAdmins(orgId)))
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
        HubUser.findByUsername(id).getOrElse(Error(Messages("organizations.admin.userNotFound", id)))
        val success = HubUser.addToOrganization(id, orgId)
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
        HubUser.findByUsername(id).getOrElse(Error(Messages("organizations.admin.userNotFound", id)))
        val success = HubUser.removeFromOrganization(id, orgId)
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

  def indexDataSets(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        val reIndexable = DataSet.findByState(DataSetState.ENABLED).toList
        reIndexable foreach { r => DataSet.updateStateAndProcessingCount(r, DataSetState.QUEUED)}
        Ok("Queued %s DataSets for indexing".format(reIndexable.size))
    }
  }

}