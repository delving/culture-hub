package controllers.organization

import play.api.i18n.Messages
import controllers._
import core.HubServices
import play.api.mvc.Action
import models.{DataSet, HubUser}

/**
 *
 * @author Gerald de Jong <gerald@delving.eu>
 * @author Manuel Bernhardt <manuel@delving.eu>
 */

object Organizations extends DelvingController {

  def index(orgId: String, language: Option[String]) = Root {
    Action {
      implicit request =>
        if (HubServices.organizationService.exists(orgId)) {
          val members: List[ListItem] = HubUser.listOrganizationMembers(orgId).flatMap(HubUser.findByUsername(_))
          val dataSets: List[ShortDataSet] = DataSet.findAllVisible(orgId, connectedUser, request.session(AccessControl.ORGANIZATIONS))
          val lang = language.getOrElse(getLang)
          Ok(Template(
            'orgId -> orgId,
            'orgName -> HubServices.organizationService.getName(orgId, "en").getOrElse(orgId),
            'isMember -> HubUser.findByUsername(connectedUser).map(u => u.organizations.contains(orgId)).getOrElse(false),
            'members -> members,
            'dataSets -> dataSets,
            'isOwner -> HubServices.organizationService.isAdmin(orgId, connectedUser),
            'currentLanguage -> lang

          ))
        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

}