/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
          Template('orgId -> o.orgId, 'orgName -> o.name.get(Lang.get()).getOrElse(o.name("en")), 'memberSince -> o.userMembership.get(connectedUser), 'members -> members, 'dataSets -> dataSets, 'isOwner -> Organization.isOwner(o.orgId, connectedUser))
        case None => NotFound(&("organizations.organization.orgNotFound", org))
      }
      case None => BadRequest
    }
  }

}