package controllers.organization

import play.mvc.results.Result
import org.bson.types.ObjectId
import models.{Organization, GrantType, Group}
import play.mvc.Util
import extensions.JJson
import models.salatContext._
import play.data.validation.Annotations._
import com.mongodb.casbah.Imports._
import controllers.{Token, ViewModel, DelvingController}
import play.Logger

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Groups extends DelvingController with OrganizationSecured {

  def list(orgId: String): Result = {
    val groups = Group.list(connectedUser, orgId).toSeq.sortWith((a, b) => a.grantType == GrantType.OWN || a.name < b.name)
    Template('groups -> groups, 'isOwner -> Organization.isOwner(orgId, connectedUser))
  }

  private def load(orgId: String, groupId: ObjectId): String = {
    groupId match {
      case null => JJson.generate(GroupViewModel())
      case id: ObjectId => Group.findOneByID(id) match {
        case None => ""
        case Some(group) => JJson.generate(GroupViewModel(id = Some(group._id), name = group.name, grantType = group.grantType.value, canChangeGrantType = group.grantType.value != 42))
      }
    }
  }

  def groups(orgId: String, groupId: ObjectId): Result = {
    if(groupId != null && !canUpdateGroup(orgId, groupId) || groupId == null && !canCreateGroup(orgId)) return Forbidden(&("user.secured.noAccess"))
    val (usersAsTokens, dataSetsAsTokens) = Group.findOneByID(groupId) match {
      case None => (JJson.generate(List()), JJson.generate(List()))
      case Some(group) =>
        val dataSets = dataSetsCollection.find("_id" $in group.dataSets, MongoDBObject("_id" -> 1, "spec" -> 1))
        (JJson.generate(group.users.map(m => Token(m, m, "user"))), JJson.generate(dataSets.map(ds => Token(ds.get("_id").toString, ds.get("spec").toString, "dataset"))))
    }
    renderArgs += ("viewModel", classOf[GroupViewModel])
    Template('id -> Option(groupId), 'data -> load(orgId, groupId), 'users -> usersAsTokens, 'dataSets -> dataSetsAsTokens)
  }

  def addUser(orgId: String, id: String, groupId: ObjectId): Result = {
    elementAction(orgId, id, groupId, "organizations.group.cannotAddUser") {
      Group.addUser(_, _)
    }
  }

  def removeUser(orgId: String, id: String, groupId: ObjectId): Result = {
    elementAction(orgId, id, groupId, "organizations.group.cannotRemoveUser") {
      Group.removeUser(_, _)
    }
  }

  def addDataset(orgId: String, id: String, groupId: ObjectId): Result = {
    elementAction(orgId, id, groupId, "organizations.group.cannotAddDataset") { (id, groupId) =>
      if(!ObjectId.isValid(id)) false
      Group.addDataSet(new ObjectId(id), groupId)
    }
  }

  def removeDataset(orgId: String, id: String, groupId: ObjectId): Result = {
    elementAction(orgId, id, groupId, "organizations.group.cannotRemoveDataset") {  (id, groupId) =>
      if(!ObjectId.isValid(id)) false
      Group.removeDataSet(new ObjectId(id), groupId)
    }
  }

  private def elementAction(orgId: String, id: String, groupId: ObjectId, messageKey: String)(op: (String, ObjectId) => Boolean): Result = {
    if(!canUpdateGroup(orgId, groupId)) return Forbidden(&("user.secured.noAccess"))
    if(id == null || groupId == null) return BadRequest
    op(id, groupId) match {
      case true => Ok
      case false => Error(&(messageKey, id, groupId))
    }
    
  }

  def update(orgId: String, groupId: ObjectId, data: String): Result = {
    if(groupId != null && !canUpdateGroup(orgId, groupId) || groupId == null && !canCreateGroup(orgId)) return Forbidden(&("user.secured.noAccess"))

    val groupModel = JJson.parse[GroupViewModel](data)
    validate(groupModel).foreach { errors => return JsonBadRequest(groupModel.copy(errors = errors)) }

    if(groupModel.grantType == GrantType.OWN.value && (groupModel.id == None || (groupModel.id != None && Group.findOneByID(groupModel.id.get) == None))) {
      reportSecurity("User %s tried to create an owners team!".format(connectedUser))
      return Forbidden("Your IP has been logged and reported to the police.")
    }

    val persisted = groupModel.id match {
      case None =>
        Group.insert(Group(node = getNode, name = groupModel.name, orgId = orgId, grantType = GrantType.get(groupModel.grantType))) match {
          case None => None
          case Some(id) =>
            groupModel.users.foreach(u => Group.addUser(u.id, id))
            groupModel.dataSets.foreach(ds => Group.addDataSet(new ObjectId(ds.id), id))
            Some(groupModel.copy(id = Some(id)))
        }
      case Some(id) =>
        Group.findOneByID(groupModel.id.get) match {
          case None => return NotFound("Group with ID %s was not found".format(id))
          case Some(g) =>
            Group.updateGroupInfo(id, groupModel.name, if(g.grantType == GrantType.OWN) GrantType.OWN.value else groupModel.grantType)
            Some(groupModel)
        }
    }

    persisted match {
      case Some(group) => Json(group)
      case None => Error(&("organizations.group.cannotSaveGroup"))
    }

  }


  @Util private def canUpdateGroup(orgId: String, groupId: ObjectId): Boolean = {
    groupId != null && Organization.isOwner(orgId, connectedUser)
  }

  @Util private def canCreateGroup(orgId: String): Boolean = Organization.isOwner(orgId, connectedUser)

}

case class GroupViewModel(id: Option[ObjectId] = None,
                          @Required name: String = "",
                          grantType: Int = GrantType.VIEW.value,
                          canChangeGrantType: Boolean = true,
                          users: List[Token] = List.empty[Token],
                          dataSets: List[Token] = List.empty[Token],
                          errors: Map[String, String] = Map.empty[String, String]) extends ViewModel