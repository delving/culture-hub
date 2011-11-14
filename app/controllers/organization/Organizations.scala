package controllers.organization

import com.mongodb.casbah.Imports._
import play.i18n.Lang
import models.{Visibility, DataSet, User, Organization}
import controllers.{AccessControl, ListItem, ShortDataSet, DelvingController}

/**
 * Public Organization controller
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Organizations extends DelvingController {

  def index(orgId: Option[String]) = {
    orgId match {
      case Some(org) => Organization.findByOrgId(org) match {
        case Some(o) =>
          val members: List[ListItem] = User.find("userName" $in o.users).toList
          val dataSets: List[ShortDataSet] = DataSet.findAllCanSee(org, connectedUser).filter(ds => ds.visibility == Visibility.PUBLIC || (ds.visibility == Visibility.PRIVATE && session.get(AccessControl.ORGANIZATIONS) != null && session.get(AccessControl.ORGANIZATIONS).split(",").contains(org))).toList
          Template('orgId -> o.orgId, 'orgName -> o.name.get(Lang.get()).getOrElse(o.name("en")), 'memberSince -> o.userMembership.get(connectedUser), 'members -> members, 'dataSets -> dataSets)
        case None => NotFound(&("organizations.organization.orgNotFound", org))
      }
      case None => BadRequest
    }
  }

}