package controllers.organization

import controllers.{ BoundController, OrganizationController }
import extensions.JJson
import play.api.i18n.Messages
import play.api.mvc.Action
import models._
import core.HubModule
import play.api.libs.ws.WS
import concurrent.Await
import concurrent.duration._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends BoundController(HubModule) with Admin

trait Admin extends OrganizationController { this: BoundController =>

  def index(orgId: String) = OrganizationAdmin {
    Action {
      implicit request =>
        if (!organizationServiceLocator.byDomain.exists(orgId)) {
          NotFound(Messages("_hub.CouldNotFindOrganization").format(orgId))
        } else {
          val membersAsTokens = JJson.generate(HubUser.dao.listOrganizationMembers(orgId).map(m => Map("id" -> m, "name" -> m)))
          val adminsAsTokens = JJson.generate(organizationServiceLocator.byDomain.listAdmins(orgId).map(a => Map("id" -> a, "name" -> a)))
          Ok(Template(
            'members -> membersAsTokens,
            'admins -> adminsAsTokens
          ))
        }
    }
  }

  /**
   * Add to organization
   */
  def addUser(orgId: String) = OrganizationAdmin {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").get
        HubUser.dao.findByUsername(id).map { user =>
          val success = HubUser.dao.addToOrganization(id, orgId)
          // TODO logging
          if (success) Ok else Error
        }.getOrElse {
          Error(Messages("_hub.CouldNotFindUser", id))
        }
    }
  }

  /**
   * Remove from organization
   */
  def removeUser(orgId: String) = OrganizationAdmin {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").get
        HubUser.dao.findByUsername(id).map { user =>
          val success = HubUser.dao.removeFromOrganization(id, orgId)
          // TODO logging
          if (success) Ok else Error
        }.getOrElse {
          Error(Messages("_hub.CouldNotFindUser", id))
        }
    }
  }

  def addAdmin(orgId: String) = OrganizationAdmin {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").get
        HubUser.dao.findByUsername(id).map { user =>
          val success = organizationServiceLocator.byDomain.addAdmin(orgId, id)
          // TODO logging
          if (success) Ok else Error
        }.getOrElse {
          Error(Messages("_hub.CouldNotFindUser", id))
        }
    }
  }

  def removeAdmin(orgId: String) = OrganizationAdmin {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").get
        val success = organizationServiceLocator.byDomain.removeAdmin(orgId, id)
        if (success) Ok else Error
    }
  }

  def solrSearchProxy(orgId: String) = OrganizationAdmin {
    Action {
      implicit request =>
        val solrQueryString: String = request.rawQueryString
        val solrServerUrl: String = String.format("%s/select?%s", configuration.solrBaseUrl, solrQueryString)

        try {
          val future = WS.url(solrServerUrl).get()
          val response = Await.result(future, 5 seconds).xml

          Ok(response)
        } catch {
          case t: Throwable => InternalServerError(t.getMessage)
        }
    }
  }

  def reProcessAll(orgId: String) = OrganizationAdmin {
    Action {
      implicit request =>

        log.info(s"[$connectedUser${configuration.orgId}] Marking all enabled DataSets to be re-process")

        DataSet.dao.findByState(DataSetState.ENABLED).foreach { dataSet =>
          DataSet.dao.updateState(dataSet, DataSetState.QUEUED, Some(connectedUser))
        }

        Ok
    }
  }

}