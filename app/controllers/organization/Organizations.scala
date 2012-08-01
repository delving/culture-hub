package controllers.organization

import play.api.i18n.Messages
import controllers._
import play.api.mvc.Action
import models.{DataSet, HubUser}
import core.{Constants, HubServices}

/**
 *
 * @author Gerald de Jong <gerald@delving.eu>
 * @author Manuel Bernhardt <manuel@delving.eu>
 */

object Organizations extends DelvingController {

  def index(orgId: String, language: Option[String]) = OrgBrowsingAction(orgId) {
    Action {
      implicit request =>
        if (HubServices.organizationService(configuration).exists(orgId)) {
          val members: List[HubUser] = HubUser.dao.listOrganizationMembers(orgId).flatMap(HubUser.dao.findByUsername(_))
          val dataSets: List[ShortDataSet] = DataSet.dao.findAllCanSee(orgId, connectedUser)
          val lang = language.getOrElse(getLang)
          Ok(Template(
            'orgId -> orgId,
            'orgName -> HubServices.organizationService(configuration).getName(orgId, "en").getOrElse(orgId),
            'isMember -> HubUser.dao.findByUsername(connectedUser).map(u => u.organizations.contains(orgId)).getOrElse(false),
            'members -> members,
            'dataSets -> dataSets,
            'currentLanguage -> lang

          ))
        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

}