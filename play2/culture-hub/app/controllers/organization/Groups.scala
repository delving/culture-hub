package controllers.organization

import org.bson.types.ObjectId
import extensions.JJson
import com.mongodb.casbah.Imports._
import play.data.validation.Constraints.Required
import models.{Organization, GrantType, Group}
import models.mongoContext._
import play.api.i18n.Messages
import controllers.{OrganizationController, ViewModel, Token}
import play.api.mvc.{Results, AnyContent, RequestHeader, Action}

/**
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */
object Groups extends OrganizationController {

  def list(orgId: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val groups = Group.list(userName, orgId).toSeq.sortWith((a, b) => a.grantType == GrantType.OWN || a.name < b.name)
        Ok(Template('groups -> groups))
    }
  }

  def groups(orgId: String, groupId: ObjectId) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        if (groupId != null && !canUpdateGroup(orgId, groupId) || groupId == null && !canCreateGroup(orgId)) {
          Forbidden(Messages("user.secured.noAccess"))
        }
        else {
          val (usersAsTokens, dataSetsAsTokens) = Group.findOneByID(groupId) match {
            case None => (JJson.generate(List()), JJson.generate(List()))
            case Some(group) =>
              val dataSets = dataSetsCollection.find("_id" $in group.dataSets, MongoDBObject("_id" -> 1, "spec" -> 1))
              (JJson.generate(group.users.map(m => Token(m, m))), JJson.generate(dataSets.map(ds => Token(ds.get("_id").toString, ds.get("spec").toString))))
          }
          renderArgs +=("viewModel", classOf[GroupViewModel])
          Ok(Template('id -> Option(groupId), 'data -> load(orgId, groupId), 'users -> usersAsTokens, 'dataSets -> dataSetsAsTokens, 'grantTypes -> GrantType.allGrantTypes.filterNot(_ == GrantType.OWN)))
        }
    }
  }

  def addUser(orgId: String, groupId: ObjectId) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").getOrElse(null)
        elementAction(orgId, id, groupId, "organizations.group.cannotAddUser") {
          Group.addUser(_, _)
        }
    }
  }

  def removeUser(orgId: String, groupId: ObjectId) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").getOrElse(null)
        elementAction(orgId, id, groupId, "organizations.group.cannotRemoveUser") {
          Group.removeUser(_, _)
        }
    }
  }

  def addDataset(orgId: String, groupId: ObjectId) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").getOrElse(null)
        elementAction(orgId, id, groupId, "organizations.group.cannotAddDataset") {
          (id, groupId) =>
            if (!ObjectId.isValid(id)) false
            Group.addDataSet(new ObjectId(id), groupId)
        }
    }
  }

  def removeDataset(orgId: String, groupId: ObjectId) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").getOrElse(null)
        elementAction(orgId, id, groupId, "organizations.group.cannotRemoveDataset") {
          (id, groupId) =>
            if (!ObjectId.isValid(id)) false
            Group.removeDataSet(new ObjectId(id), groupId)
        }
    }
  }

  private def elementAction(orgId: String, id: String, groupId: ObjectId, messageKey: String)(op: (String, ObjectId) => Boolean)(implicit request: RequestHeader) = {
    if (!canUpdateGroup(orgId, groupId)) {
      Forbidden(Messages("user.secured.noAccess"))
    } else if (id == null || groupId == null) {
      Results.BadRequest
    } else {
      op(id, groupId) match {
        case true => Ok
        case false => Error(Messages(messageKey, id, groupId))
      }
    }
  }


  def update(orgId: String): Action[AnyContent] = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val groupId = request.body.getFirstAsObjectId("groupId")
        val data = request.body.getFirstAsString("data").getOrElse("")

        if (groupId != None && !canUpdateGroup(orgId, groupId.get) || groupId == None && !canCreateGroup(orgId)) {
          Forbidden(Messages("user.secured.noAccess"))
        }
        else {
          Forbidden(Messages("user.secured.noAccess"))

          val groupModel = JJson.parse[GroupViewModel](data)

          // TODO MIGRATION
          // validate(groupModel).foreach { errors => return JsonBadRequest(groupModel.copy(errors = errors)) }
          val grantType = try {
            GrantType.get(groupModel.grantType)
          } catch {
            case t =>
              reportSecurity("Attempting to save Group with grantType " + groupModel.grantType)
              return Action {
                BadRequest("Invalid GrantType " + groupModel.grantType)
              }
          }

          if (grantType == GrantType.OWN && (groupModel.id == None || (groupModel.id != None && Group.findOneByID(groupModel.id.get) == None))) {
            reportSecurity("User %s tried to create an owners team!".format(connectedUserId))
            return Action {
              Forbidden("Your IP has been logged and reported to the police.")
            }
          }

          val persisted = groupModel.id match {
            case None =>
              Group.insert(Group(node = getNode, name = groupModel.name, orgId = orgId, grantType = grantType.key)) match {
                case None => None
                case Some(id) =>
                  groupModel.users.foreach(u => Group.addUser(u.id, id))
                  groupModel.dataSets.foreach(ds => Group.addDataSet(new ObjectId(ds.id), id))
                  Some(groupModel.copy(id = Some(id)))
              }
            case Some(id) =>
              Group.findOneByID(groupModel.id.get) match {
                case None => return Action {
                  NotFound("Group with ID %s was not found".format(id))
                }
                case Some(g) =>
                  g.grantType match {
                    case GrantType.OWN.key => // do nothing
                    case _ => Group.updateGroupInfo(id, groupModel.name, grantType)
                  }
                  Some(groupModel)
              }
          }

          persisted match {
            case Some(group) => Json(group)
            case None => Error(Messages("organizations.group.cannotSaveGroup"))
          }


        }
    }
  }

  private def load(orgId: String, groupId: ObjectId): String = {
    groupId match {
      case null => JJson.generate(GroupViewModel())
      case id: ObjectId => Group.findOneByID(id) match {
        case None => ""
        case Some(group) => JJson.generate(GroupViewModel(id = Some(group._id), name = group.name, grantType = group.grantType, canChangeGrantType = group.grantType != GrantType.OWN.key))
      }
    }
  }

  private def canUpdateGroup(orgId: String, groupId: ObjectId)(implicit request: RequestHeader): Boolean = {
    groupId != null && Organization.isOwner(orgId, userName)
  }

  private def canCreateGroup(orgId: String)(implicit request: RequestHeader): Boolean = Organization.isOwner(orgId, userName)
}

case class GroupViewModel(id: Option[ObjectId] = None,
                          @Required name: String = "",
                          grantType: String = GrantType.VIEW.key,
                          canChangeGrantType: Boolean = true,
                          users: List[Token] = List.empty[Token],
                          dataSets: List[Token] = List.empty[Token],
                          errors: Map[String, String] = Map.empty[String, String]) extends ViewModel