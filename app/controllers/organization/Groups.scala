package controllers.organization

import org.bson.types.ObjectId
import extensions.JJson
import com.mongodb.casbah.Imports._
import models.{DomainConfiguration, Role, Group}
import models.mongoContext._
import play.api.i18n.Messages
import controllers.{OrganizationController, ViewModel, Token}
import play.api.mvc.{Results, AnyContent, RequestHeader, Action}
import play.api.data.Forms._
import extensions.Formatters._
import play.api.data.Form
import core.HubServices
import core.access.ResourceType
import collection.JavaConverters._
import play.api.Logger

/**
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */
object Groups extends OrganizationController {

  def list(orgId: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val groups = Group.dao.list(userName, orgId).toSeq.sortWith((a, b) => a.grantType == Role.OWN || a.name < b.name)
        Ok(Template('groups -> groups))
    }
  }

  def groups(orgId: String, groupId: Option[ObjectId]) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        if (groupId != None && !canUpdateGroup(orgId, groupId.get) || groupId == None && !canCreateGroup(orgId)) {
          Forbidden(Messages("user.secured.noAccess"))
        } else {
          val group: Option[Group] = groupId.flatMap(Group.dao.findOneById(_))
          val (usersAsTokens, dataSetsAsTokens) = group match {
            case None => (JJson.generate(List()), JJson.generate(List()))
            case Some(g) =>
              val userTokens = g.users.map(m => Token(m, m))
              val resourceTokens = g.resources.map(r => Token(r.getResourceKey, r.getResourceKey))
              (JJson.generate(userTokens), JJson.generate(resourceTokens))
          }
          Ok(Template(
            'id -> groupId,
            'data -> load(orgId, groupId),
            'groupForm -> GroupViewModel.groupForm,
            'users -> usersAsTokens,
            'dataSets -> dataSetsAsTokens,
            'grantTypes -> Role.allRoles(configuration).
                    filterNot(_ == Role.OWN).
                    map(role => (role.key -> role.getDescription(lang))).
                    toMap.asJava
          ))
        }
    }
  }

  def addUser(orgId: String, groupId: ObjectId) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").getOrElse(null)
        elementAction(orgId, id, groupId, "organizations.group.cannotAddUser") { (userName, groupId) =>
          Group.dao.addUser(orgId, userName, groupId)
        }
    }
  }

  def removeUser(orgId: String, groupId: ObjectId) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").getOrElse(null)
        elementAction(orgId, id, groupId, "organizations.group.cannotRemoveUser") { (userName, groupId) =>
          Group.dao.removeUser(orgId, userName, groupId)
        }
    }
  }

  // TODO generify
  def addDataset(orgId: String, groupId: ObjectId) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").getOrElse(null)
        elementAction(orgId, id, groupId, "organizations.group.cannotAddDataset") {
          (id, groupId) =>
            Group.dao.addResource(orgId, id, ResourceType("dataSet"), groupId)
        }
    }
  }

  // TODO generify
  def removeDataset(orgId: String, groupId: ObjectId) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val id = request.body.getFirstAsString("id").getOrElse(null)
        elementAction(orgId, id, groupId, "organizations.group.cannotRemoveDataset") {
          (id, groupId) =>
            Group.dao.removeResource(orgId, id, ResourceType("dataSet"), groupId)
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

  def remove(orgId: String, groupId: Option[ObjectId]) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        if(!groupId.isDefined) {
          Results.BadRequest
        } else {
          Group.dao.remove(MongoDBObject("_id" -> groupId, "orgId" -> orgId))
          Ok
        }
    }
  }


  def submit(orgId: String): Action[AnyContent] = OrgMemberAction(orgId) {
    Action {
      implicit request =>

        GroupViewModel.groupForm.bindFromRequest.fold(
          formWithErrors => handleValidationError(formWithErrors),
          groupForm => {
            Logger("CultureHub").debug("Received group submission: " + groupForm)
            val groupId = groupForm.id
            if(groupForm.id != None && !canUpdateGroup(orgId, groupId.get) || groupId == None && !canCreateGroup(orgId)) {
              Forbidden(Messages("user.secured.noAccess"))
            } else {
                  val role = try {
                    Role.get(groupForm.grantType)
                  } catch {
                    case t: Throwable =>
                      reportSecurity("Attempting to save Group with role " + groupForm.grantType)
                      return Action {
                        BadRequest("Invalid Role " + groupForm.grantType)
                      }
                  }

                  if (role == Role.OWN && (groupForm.id == None || (groupForm.id != None && Group.dao.findOneById(groupForm.id.get) == None))) {
                    reportSecurity("User %s tried to create an owners team!".format(connectedUser))
                    return Action {
                      Forbidden("Your IP has been logged and reported to the police.")
                    }
                  }

                  val persisted = groupForm.id match {

                    case None =>
                      Group.dao.insert(
                        Group(
                          name = groupForm.name,
                          orgId = orgId,
                          grantType = role.key
                        )
                      ) match {
                        case None => None
                        case Some(id) =>
                          groupForm.users.foreach(u => Group.dao.addUser(orgId, u.id, id))
                          groupForm.dataSets.foreach(ds => Group.dao.addResource(orgId, ds.id, ResourceType("dataSet"), id))
                          Some(groupForm.copy(id = Some(id)))
                      }
                    case Some(id) =>

                      Group.dao.findOneById(groupForm.id.get) match {
                        case None => return Action {
                          NotFound("Group with ID %s was not found".format(id))
                        }
                        case Some(g) =>
                          g.grantType match {
                            case Role.OWN.key => // do nothing
                            case _ => Group.dao.updateGroupInfo(id, groupForm.name, role)
                          }
                          Some(groupForm)
                      }
                  }

                  persisted match {
                    case Some(group) => Json(group)
                    case None => Error(Messages("organizations.group.cannotSaveGroup"))
                  }
                }
        })
    }
  }

  private def load(orgId: String, groupId: Option[ObjectId])(implicit configuration: DomainConfiguration): String = {
    val resourceRoles = Role.allRoles(configuration).filterNot(_.resourceType.isEmpty)
    val defaultGroupViewModel = GroupViewModel(
      grantType = Role.allRoles(configuration).head.key,
      rolesWithResources = resourceRoles.map(_.key),
      rolesWithResourceAdmin = Seq.empty,
      rolesResourceType = resourceRoles.map(r => RoleResourceType(r.key, r.resourceType.get.resourceType, Messages("accessControl.resourceType." + r.resourceType.get.resourceType)))
    )

    groupId.flatMap(Group.dao.findOneById(_)) match {
      case None => JJson.generate(defaultGroupViewModel)
      case Some(group) => JJson.generate(
        GroupViewModel(
          id = Some(group._id),
          name = group.name,
          grantType = group.grantType,
          canChangeGrantType = group.grantType != Role.OWN.key,
          users = group.users.map(u => Token(u, u)),
          rolesWithResources = defaultGroupViewModel.rolesWithResources,
          rolesWithResourceAdmin = defaultGroupViewModel.rolesWithResourceAdmin,
          rolesResourceType = defaultGroupViewModel.rolesResourceType
        )
      )
    }
  }

  private def canUpdateGroup(orgId: String, groupId: ObjectId)(implicit request: RequestHeader): Boolean = {
    groupId != null && HubServices.organizationService(configuration).isAdmin(orgId, userName)
  }

  private def canCreateGroup(orgId: String)(implicit request: RequestHeader): Boolean = HubServices.organizationService(configuration).isAdmin(orgId, userName)
}

case class GroupViewModel(id: Option[ObjectId] = None,
                          name: String = "",
                          grantType: String,
                          canChangeGrantType: Boolean = true,
                          users: List[Token] = List.empty[Token],
                          rolesWithResources: Seq[String] = Seq.empty,
                          rolesWithResourceAdmin: Seq[String] = Seq.empty,
                          rolesResourceType: Seq[RoleResourceType] = Seq.empty,
                          dataSets: List[Token] = List.empty[Token],
                          errors: Map[String, String] = Map.empty[String, String]) extends ViewModel

case class RoleResourceType(roleKey: String, resourceType: String, resourceTypeName: String)

object GroupViewModel {


  val groupForm: Form[GroupViewModel] = Form(
    mapping(
      "id" -> optional(of[ObjectId]),
      "name" -> nonEmptyText,
      "grantType" -> nonEmptyText,
      "canChangeGrantType" -> boolean,
      "users" -> Groups.tokenListMapping,
      "rolesWithResources" -> seq(nonEmptyText),
      "rolesWithResourceAdmin" -> seq(nonEmptyText),
      "rolesResourceType" -> seq(
        mapping(
        "roleKey" -> nonEmptyText,
        "resourceType" -> nonEmptyText,
        "resourceTypeName" -> nonEmptyText
        )(RoleResourceType.apply)(RoleResourceType.unapply)
      ),
      "dataSets" -> Groups.tokenListMapping,
      "errors" -> of[Map[String, String]]
    )(GroupViewModel.apply)(GroupViewModel.unapply)
  )
  
}