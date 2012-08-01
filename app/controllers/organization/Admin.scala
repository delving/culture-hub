package controllers.organization

import controllers.OrganizationController
import extensions.JJson
import play.api.i18n.Messages
import play.api.mvc.Action
import models._
import core.HubServices
import play.api.libs.ws.WS

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends OrganizationController {

  def index(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        if (!HubServices.organizationService(configuration).exists(orgId)) {
          NotFound(Messages("organizations.organization.orgNotFound").format(orgId))
        } else {
          val membersAsTokens = JJson.generate(HubUser.dao.listOrganizationMembers(orgId).map(m => Map("id" -> m, "name" -> m)))
          Ok(Template('members -> membersAsTokens, 'owners -> HubServices.organizationService(configuration).listAdmins(orgId)))
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
        HubUser.dao.findByUsername(id).getOrElse(Error(Messages("organizations.admin.userNotFound", id)))
        val success = HubUser.dao.addToOrganization(id, orgId)
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
        HubUser.dao.findByUsername(id).getOrElse(Error(Messages("organizations.admin.userNotFound", id)))
        val success = HubUser.dao.removeFromOrganization(id, orgId)
        // TODO logging
        if (success) Ok else Error
    }
  }

  def indexDataSets(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        val reIndexable = DataSet.dao.findByState(DataSetState.ENABLED).toList
        reIndexable foreach { r => DataSet.dao.updateState(r, DataSetState.QUEUED, Some(connectedUser)) }
        Ok("Queued %s DataSets for processing".format(reIndexable.size))
    }
  }

  def solrSearchProxy(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        val solrQueryString: String = request.rawQueryString
        val solrServerUrl: String = String.format("%s/select?%s", configuration.solrBaseUrl, solrQueryString)

        val response = WS.url(solrServerUrl).get().await.fold(
          error => Left(error),
          success => Right(success)
        )

        response match {
          case Right(r) => Ok(r.xml)
          case Left(error) => InternalServerError(error.getMessage)
        }
    }
  }

}