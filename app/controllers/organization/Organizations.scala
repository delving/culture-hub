package controllers.organization

import play.api.mvc.Action
import models.{Visibility, DataSet, HubUser}
import play.api.i18n.Messages
import controllers._
import core.HubServices

/**
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

object Organizations extends DelvingController {

  def index(orgId: String) = Root {
    Action {
      implicit request =>
        if(HubServices.organizationService.exists(orgId)) {
            val members: List[ListItem] = HubUser.listOrganizationMembers(orgId).flatMap(HubUser.findByUsername(_))
            val dataSets: List[ShortDataSet] =
              DataSet.findAllCanSee(orgId, connectedUser).
                filter(ds =>
                  ds.visibility == Visibility.PUBLIC ||
                  (
                    ds.visibility == Visibility.PRIVATE &&
                    session.get(AccessControl.ORGANIZATIONS) != null &&
                    request.session(AccessControl.ORGANIZATIONS).split(",").contains(orgId)
                  )
            ).toList
            Ok(Template(
              'orgId -> orgId,
              'orgName -> HubServices.organizationService.getName(orgId, "en").getOrElse(orgId),
              'isMember -> HubUser.findByUsername(connectedUser).map(u => u.organizations.contains(orgId)).getOrElse(false),
              'members -> members,
              'dataSets -> dataSets,
              'isOwner -> HubServices.organizationService.isAdmin(orgId, connectedUser)
            ))
        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

}