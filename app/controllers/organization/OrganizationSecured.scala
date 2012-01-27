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

import play.mvc.Before
import play.mvc.results.Result
import controllers.{AccessControl, DelvingController, Secure}
import com.mongodb.casbah.commons.MongoDBObject
import models.{Group, GrantType, Organization}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait OrganizationSecured extends Secure { self: DelvingController =>

  @Before(priority = 0) def checkUser(): Result = {
    val orgId = params.get("orgId")
    if(orgId == null || orgId.isEmpty) return Error("How did you even get here?")
    val organizations = session.get(AccessControl.ORGANIZATIONS)
    if(organizations == null || organizations.isEmpty) return Forbidden(&("user.secured.noAccess"))
    if(!organizations.split(",").contains(orgId)) return Forbidden(&("user.secured.noAccess"))
    renderArgs += ("orgId" -> orgId)
    renderArgs += ("isOwner" -> Organization.isOwner(orgId, connectedUser))
    renderArgs += ("isCMSAdmin" -> (Organization.isOwner(orgId, connectedUser) || (Group.count(MongoDBObject("users" -> connectedUser, "grantType" -> GrantType.CMS.key)) == 0)))
    Continue
  }

  def isOwner: Boolean = renderArgs("isOwner").get.asInstanceOf[Boolean]

}