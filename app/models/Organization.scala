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
import salatContext._
import com.mongodb.casbah.Imports._
import java.util.Date
import com.mongodb.casbah.WriteConcern

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Organization(_id: ObjectId = new ObjectId,
                        node: String, // node on which this organization runs
                        orgId: String, // identifier of this organization, unique in the world, used in the URL
                        name: Map[String, String] = Map.empty[String, String], // language - orgName
                        users: List[String] = List.empty[String], // member usernames
                        userMembership: Map[String, Date] = Map.empty[String, Date]) // membership information

object Organization extends SalatDAO[Organization, ObjectId](organizationCollection) {

  def fetchName(orgId: String) = organizationCollection.findOne(MongoDBObject("orgId" -> orgId), MongoDBObject("name.en" -> 1)) match {
    case None => None
    case Some(org) => Some(org.getAs[String]("name.en"))
  }

  def findByOrgId(orgId: String) = Organization.findOne(MongoDBObject("orgId" -> orgId))
  def isOwner(orgId: String, userName: String) = Group.count(MongoDBObject("orgId" -> orgId, "users" -> userName, "grantType.value" -> GrantType.OWN.value)) > 0

  def listOwnersAndId(orgId: String) = Group.findOne(MongoDBObject("orgId" -> orgId, "grantType.value" -> GrantType.OWN.value)) match {
    case Some(g) => (Some(g._id), g.users)
    case None => (None, List())
  }

  def addUser(orgId: String, userName: String): Boolean = {
    // TODO FIXME make this operation safe
    Organization.update(MongoDBObject("orgId" -> orgId), $addToSet ("users" -> userName), false, false, WriteConcern.Safe)
    val mu = "userMembership." + userName
    Organization.update(MongoDBObject("orgId" -> orgId), $set (mu -> new Date()), false, false, WriteConcern.Safe)
    User.update(MongoDBObject("userName" -> userName), $addToSet ("organizations" -> orgId), false, false, WriteConcern.Safe)
    true
  }

  def removeUser(orgId: String, userName: String): Boolean = {
    // TODO FIXME make this operation safe
    Organization.update(MongoDBObject("orgId" -> orgId), $pull ("users" -> userName), false, false, WriteConcern.Safe)
    val mu = "userMembership." + userName
    Organization.update(MongoDBObject("orgId" -> orgId), $unset (mu), false, false, WriteConcern.Safe)
    User.update(MongoDBObject("userName" -> userName), $pull ("organizations" -> orgId), false, false, WriteConcern.Safe)

    // remove from all groups
    Group.findDirectMemberships(userName, orgId).foreach {
      group => Group.removeUser(userName, group._id)
    }

    true
  }

}

case class Group(_id: ObjectId = new ObjectId,
                 node: String,
                 name: String,
                 orgId: String,
                 grantType: GrantType,
                 dataSets: List[ObjectId] = List.empty[ObjectId],
                 users: List[String] = List.empty[String])

object Group extends SalatDAO[Group, ObjectId](groupCollection) {

  /** lists all groups a user has access to for a given organization **/
  def list(userName: String, orgId: String) = {
    if(Organization.isOwner(orgId, userName)) {
      Group.find(MongoDBObject("orgId" -> orgId))
    } else {
      Group.find(MongoDBObject("users" -> userName, "orgId" -> orgId))
    }
  }

  def findDirectMemberships(userName: String, orgId: String) = Group.find(MongoDBObject("orgId" -> orgId, "users" -> userName))

  def addUser(userName: String, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    User.update(MongoDBObject("userName" -> userName), $addToSet ("groups" -> groupId), false, false, WriteConcern.Safe)
    Group.update(MongoDBObject("_id" -> groupId), $addToSet ("users" -> userName), false, false, WriteConcern.Safe)
    true
  }

  def removeUser(userName: String, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    User.update(MongoDBObject("userName" -> userName), $pull ("groups" -> groupId), false, false, WriteConcern.Safe)
    Group.update(MongoDBObject("_id" -> groupId), $pull ("users" -> userName), false, false, WriteConcern.Safe)
    true
  }

  def addDataSet(id: ObjectId, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    Group.update(MongoDBObject("_id" -> groupId), $addToSet ("dataSets" -> id), false, false, WriteConcern.Safe)
    true
  }

  def removeDataSet(id: ObjectId, groupId: ObjectId): Boolean = {
    // TODO FIXME make this operation safe
    Group.update(MongoDBObject("_id" -> groupId), $pull ("dataSets" -> id), false, false, WriteConcern.Safe)
    true
  }

  def updateGroupInfo(groupId: ObjectId, name: String, grantType: GrantType): Boolean = {
    Group.update(MongoDBObject("_id" -> groupId), $set("name" -> name, "grantType.value" -> grantType.value))
    true
  }

}

case class GrantType(value: Int)
object GrantType {
  def illegal(value: Int) = throw new IllegalArgumentException("Illegal value %s for GrantType".format(value))
  val VIEW = GrantType(0)
  val MODIFY = GrantType(10)
  val CMS = GrantType(30)
  val OWN = GrantType(42)
  val values: Map[Int, String] = Map(VIEW.value -> "view", MODIFY.value -> "modify", CMS.value -> "cms", OWN.value -> "own")
  def name(value: Int): String = values.get(value).getOrElse(illegal(value))
  def name(gt: GrantType): String = values.get(gt.value).getOrElse(illegal(gt.value))
  def get(value: Int) = {
    if(!values.contains(value)) illegal(value)
    GrantType(value)
  }

}