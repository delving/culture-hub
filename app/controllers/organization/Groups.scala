package controllers.organization

import controllers.DelvingController
import play.mvc.results.Result
import org.bson.types.ObjectId
import models.{Organization, GrantType, Group}
import play.mvc.Util
import extensions.JJson

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Groups extends DelvingController with OrganizationSecured {

  def list(orgId: String): Result = {
    val groups = Group.list(connectedUser, orgId).toSeq.sortWith((a, b) => a.grantType == GrantType.OWN || a.name < b.name)
    Template('groups -> groups)
  }

  def groups(groupId: ObjectId): Result = {
    if(groupId != null && !canUpdateGroup(groupId)) return Forbidden(&("user.secured.noAccess"))
    val usersAsTokens = Group.findOneByID(groupId) match {
      case None => JJson.generate(List())
      case Some(group) => JJson.generate(group.users.map(m => ("id" -> m, "name" -> m)).toMap)
    }
    Template('groupId -> groupId, 'members -> usersAsTokens)
  }

  def addUser(orgId: String, userName: String, groupId: ObjectId): Result = {
    if(!canUpdateGroup(groupId)) return Forbidden(&("user.secured.noAccess"))
    Group.addUser(userName, groupId) match {
      case true => Ok
      case false => Error(&("organizations.group.cannotAddUser", userName, groupId))
    }
  }

  def removeUser(orgId: String, userName: String, groupId: ObjectId): Result = {
    if(!canUpdateGroup(groupId)) return Forbidden(&("user.secured.noAccess"))
    Group.removeUser(userName, groupId) match {
      case true => Ok
      case false => Error(&("organizations.group.cannotRemoveUser", userName, groupId))
    }
  }

//  def update(groupId: ObjectId): Result = {
//    if(!canUpdateGroup(groupId)) return Forbidden(&("user.secured.noAccess"))
//
//    Ok
//  }


  @Util private def canUpdateGroup(groupId: ObjectId): Boolean = {
    groupId != null && Organization.isOwner(connectedUser)
  }

}