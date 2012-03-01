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

package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import mongoContext._
import com.mongodb.casbah.Imports._
import java.util.Date

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Organization(_id: ObjectId = new ObjectId,
                        node: String, // node on which this organization runs
                        orgId: String, // identifier of this organization, unique in the world, used in the URL
                        name: Map[String, String] = Map.empty[String, String], // language - orgName
                        users: List[String] = List.empty[String], // member usernames
                        userMembership: Map[String, Date] = Map.empty[String, Date], // membership information
                        admins: List[String] = List.empty[String] // admin usernames
                       )

object Organization extends SalatDAO[Organization, ObjectId](organizationCollection) {

  def fetchName(orgId: String) = Organization.findByOrgId(orgId) match {
    case None => None
    case Some(org) => org.name.get("en")
  }

  def findByOrgId(orgId: String) = Organization.findOne(MongoDBObject("orgId" -> orgId))
  def isOwner(orgId: String, userName: String) = Organization.count(MongoDBObject("orgId" -> orgId, "admins" -> userName)) > 0

  def addAdmin(orgId: String, userName: String): Boolean = {
    try {
      Organization.update(MongoDBObject("orgId" -> orgId), $addToSet("admins" -> userName))
    } catch {
      case _ => return false
    }
    true
  }
  def removeAdmin(orgId: String, userName: String): Boolean = {
    try {
      Organization.update(MongoDBObject("orgId" -> orgId), $pull("admins" -> userName))
    } catch {
      case _ => return false
    }
    true
  }

  def listOwnersAndId(orgId: String) = Group.findOne(MongoDBObject("orgId" -> orgId, "grantType" -> GrantType.OWN.key)) match {
    case Some(g) => (Some(g._id), g.users)
    case None => (None, List())
  }

}