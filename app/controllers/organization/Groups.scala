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

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Groups extends DelvingController with OrganizationSecured {

  def list(orgId: String): Result = {
    val groups = Group.list(connectedUser, orgId).toSeq.sortWith((a, b) => a.grantType == GrantType.OWN || a.name < b.name)
    Template('groups -> groups, 'isOwner -> Organization.isOwner(connectedUser))
  }

  def load(orgId: String, groupId: ObjectId): Result = {
    if(groupId != null && !canUpdateGroup(groupId) || groupId == null && !canCreateGroup) return Forbidden(&("user.secured.noAccess"))
    groupId match {
      case null => Json(GroupViewModel())
      case id: ObjectId => Group.findOneByID(id) match {
        case None => NotFound("Could not find group with ID %s".format(id))
        case Some(group) => Json(GroupViewModel(id = Some(group._id), name = group.name, grantType = group.grantType.value))
      }
    }
  }

  def groups(groupId: ObjectId): Result = {
    if(groupId != null && !canUpdateGroup(groupId) || groupId == null && !canCreateGroup) return Forbidden(&("user.secured.noAccess"))
    val (usersAsTokens, dataSetsAsTokens) = Group.findOneByID(groupId) match {
      case None => (JJson.generate(List()), JJson.generate(List()))
      case Some(group) =>
        val dataSets = dataSetsCollection.find("_id" $in group.dataSets, MongoDBObject("_id" -> 1, "spec" -> 1))
        (JJson.generate(group.users.map(m => Token(m, m))), JJson.generate(dataSets.map(ds => Token(ds.get("_id").toString, ds.get("spec").toString))))
    }
    renderArgs += ("viewModel", classOf[GroupViewModel])
    Template('id -> Option(groupId), 'users -> usersAsTokens, 'dataSets -> dataSetsAsTokens)
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
    if(!canUpdateGroup(groupId)) return Forbidden(&("user.secured.noAccess"))
    if(id == null || groupId == null) return BadRequest
    op(id, groupId) match {
      case true => Ok
      case false => Error(&(messageKey, id, groupId))
    }
    
  }

  def update(orgId: String, groupId: ObjectId, data: String): Result = {
    if(groupId != null && !canUpdateGroup(groupId) || groupId == null && !canCreateGroup) return Forbidden(&("user.secured.noAccess"))

    val groupModel = JJson.parse[GroupViewModel](data)
    validate(groupModel).foreach { errors => return JsonBadRequest(groupModel.copy(errors = errors)) }

    // TODO we use a validation check to make sure someone does not try to make the group have more rights than allowed. In fact if this happens we should send a big warning e-mail and ban the user.

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
        Group.updateGroupInfo(id, groupModel.name, groupModel.grantType)
        Some(groupModel)
    }

    persisted match {
      case Some(group) => Json(group)
      case None => Error(&("organizations.group.cannotSaveGroup"))
    }

  }


  @Util private def canUpdateGroup(groupId: ObjectId): Boolean = {
    groupId != null && Organization.isOwner(connectedUser)
  }

  @Util private def canCreateGroup: Boolean = Organization.isOwner(connectedUser)

}

case class GroupViewModel(id: Option[ObjectId] = None,
                          @Required name: String = "",
                          @Range(min=0, max=10) grantType: Int = GrantType.VIEW.value,
                          users: List[Token] = List.empty[Token],
                          dataSets: List[Token] = List.empty[Token],
                          errors: Map[String, String] = Map.empty[String, String]) extends ViewModel