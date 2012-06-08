package controllers.organization

import controllers.OrganizationController
import extensions.JJson
import play.api.i18n.Messages
import play.api.mvc.Action
import util.ThemeHandler
import models._
import core.HubServices
import core.search.Params
import play.api.Play.current
import play.api.libs.ws.WS
import java.io.File
import play.api.{Logger, Play}

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

  def indexDataSets(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        val reIndexable = DataSet.findByState(DataSetState.ENABLED).toList
        reIndexable foreach { r => DataSet.updateStateAndProcessingCount(r, DataSetState.QUEUED)}
        Ok("Queued %s DataSets for indexing".format(reIndexable.size))
    }
  }

  def solrSearchProxy(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        val solrQueryString: String = request.rawQueryString
        val solrServerUrl: String = String.format("%s/select?%s", Play.configuration.getString("solr.baseUrl").getOrElse("http://localhost:8983/solr/core2"), solrQueryString)

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

  def redeploy(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        val parsing = DataSet.findByState(DataSetState.PARSING).toList
        val processing = DataSet.findByState(DataSetState.PROCESSING).toList

        if(parsing.size == 0 && processing.size == 0) {
          val pb = new ProcessBuilder(Seq("/bin/sh", new File(Play.application.path, "redeploy.sh").getAbsolutePath, "&") : _ *)
          pb.start()
          Logger("CultureHub").info("Redeploying of CultureHub triggered by HTTP request")
          Ok("Ok, redeploying")
        } else {
          val parsingSets = parsing.map(ds => "%s (%s parsed)".format(ds.spec, ds.details.total_records)).mkString(", ")
          val processingSets = processing.map(ds => "%s (%s/%s)".format(ds.spec, ds.details.indexing_count, ds.details.total_records)).mkString(", ")
          Ok(
            """Can't redeploy:
              | parsing:
              | %s
              |
              | processing:
              | %s
            """.stripMargin.format(parsingSets, processingSets))
        }



    }
  }

}