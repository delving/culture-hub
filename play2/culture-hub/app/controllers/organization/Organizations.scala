package controllers.organization

import play.api.mvc.Action
import com.mongodb.casbah.Imports._
import models.{Visibility, DataSet, User, Organization}
import play.api.i18n.Messages
import controllers._

/**
 * todo: javadoc
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

object Organizations extends DelvingController {

  def index(orgId: String) = Root {
    Action {
      implicit request =>
        Organization.findByOrgId(orgId) match {
          case Some(o) =>
            val members: List[ListItem] = User.find(Authentication.USERNAME $in o.users).toList
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
              'orgId -> o.orgId,
              'orgName -> o.name.get(getLang).getOrElse(o.name("en")),
              'memberSince -> o.userMembership.get(connectedUser),
              'members -> members,
              'dataSets -> dataSets,
              'isOwner -> Organization.isOwner(o.orgId, connectedUser)
            ))
          case None => NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

}