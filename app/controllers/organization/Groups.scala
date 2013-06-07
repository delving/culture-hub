package controllers.organization

import extensions.JJson
import com.mongodb.casbah.Imports._
import models._
import models.HubMongoContext._
import play.api.i18n.Messages
import controllers.{ OrganizationController, Token }
import play.api.mvc.{ Results, AnyContent, RequestHeader, Action }
import play.api.data.Forms._
import extensions.Formatters._
import play.api.data.Form
import core.CultureHubPlugin
import core.access.{ ResourceType, Resource }
import collection.JavaConverters._
import play.api.Logger
import scala.Some
import com.escalatesoft.subcut.inject.BindingModule

/**
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */
class Groups(implicit val bindingModule: BindingModule) extends OrganizationController {

  def list(orgId: String) = OrganizationMember {
    Action {
      implicit request =>
        val groups = Group.dao.list(userName, orgId).filterNot(_.isSystemGroup).map { group =>
          GroupListModel(
            id = group._id.toString,
            name = group.name,
            description = Role.get(group.roleKey).getDescription(lang),
            size = group.users.size
          )
        }.toSeq

        val groupsData = Map("groups" -> groups)

        Ok(Template('groups -> JJson.generate(groupsData)))
    }
  }

  def groups(orgId: String, groupId: Option[ObjectId]) = OrganizationMember {
    Action {
      implicit request =>
        if (groupId != None && !canUpdateGroup(orgId, groupId.get) || groupId == None && !canCreateGroup(orgId)) {
          Forbidden(Messages("hub.YouDoNotHaveAccess"))
        } else {
          val group: Option[Group] = groupId.flatMap(Group.dao.findOneById(_))
          val usersAsTokens = group match {
            case None => (JJson.generate(List()))
            case Some(g) =>
              val userTokens = g.users.map(m => Token(m, m))
              JJson.generate(userTokens)
          }
          Ok(Template(
            'id -> groupId,
            'data -> load(orgId, groupId),
            'groupForm -> GroupViewModel.groupForm,
            'users -> usersAsTokens,
            'roles -> Role.allPrimaryRoles(configuration).
              filterNot(_ == Role.OWN).
              map(role => (role.key -> role.getDescription(lang))).
              toMap.asJava
          ))
        }
    }
  }

  def remove(orgId: String, groupId: Option[ObjectId]) = OrganizationAdmin {
    Action {
      implicit request =>
        if (!groupId.isDefined) {
          Results.BadRequest
        } else {
          Group.dao.remove(MongoDBObject("_id" -> groupId, "orgId" -> orgId))
          Ok
        }
    }
  }

  def submit(orgId: String): Action[AnyContent] = OrganizationMember {
    Action {
      implicit request =>

        GroupViewModel.groupForm.bindFromRequest.fold(
          formWithErrors => handleValidationError(formWithErrors),
          groupForm => {
            Logger("CultureHub").debug("Received group submission: " + groupForm)
            val groupId = groupForm.id
            if (groupForm.id != None && !canUpdateGroup(orgId, groupId.get) || groupId == None && !canCreateGroup(orgId)) {
              Forbidden(Messages("hub.YouDoNotHaveAccess"))
            } else {
              val role = try {
                Role.get(groupForm.roleKey)
              } catch {
                case t: Throwable =>
                  reportSecurity("Attempting to save Group with role " + groupForm.roleKey)
                  return Action {
                    BadRequest("Invalid Role " + groupForm.roleKey)
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
                      roleKey = role.key
                    )
                  ) match {
                      case None => None
                      case Some(id) =>
                        groupForm.users.foreach(u => Group.dao.addUser(orgId, u.id, id))
                        groupForm.resources.foreach(r => Group.dao.addResource(orgId, r.id, role.resourceType.get, id))
                        Some(groupForm.copy(id = Some(id)))
                    }
                case Some(id) =>

                  Group.dao.findOneById(groupForm.id.get) match {
                    case None => return Action {
                      NotFound("Group with ID %s was not found".format(id))
                    }
                    case Some(g) =>
                      g.roleKey match {
                        case Role.OWN.key => // do nothing
                        case _ =>

                          val resources: Seq[Resource] = role.resourceType.map { resourceType =>
                            val lookup = CultureHubPlugin.getResourceLookup(role.resourceType.get).get
                            groupForm.resources.flatMap { resourceToken =>
                              lookup.findResourceByKey(orgId, resourceToken.id)
                            }
                          }.getOrElse {
                            Seq.empty
                          }
                          Group.dao.updateGroupInfo(id, groupForm.name, role, groupForm.users.map(_.id), resources.map(r => PersistedResource(r)))
                          groupForm.users.foreach(u => Group.dao.addUser(orgId, u.id, id))

                      }
                      Some(groupForm)
                  }
              }

              persisted match {
                case Some(group) => Json(group)
                case None => Error(Messages("hub.CouldNotSaveGroup"))
              }
            }
          })
    }
  }

  def searchResourceTokens(orgId: String, resourceType: String, q: String) = OrganizationMember {
    Action {
      implicit request =>
        val maybeLookup = CultureHubPlugin.getResourceLookup(ResourceType(resourceType))
        maybeLookup.map { lookup =>
          val tokens = lookup.findResources(orgId, q).map { resource =>
            Token(resource.getResourceKey, resource.getResourceKey, Some(resource.getResourceType.resourceType))
          }
          Json(tokens)
        }.getOrElse(
          Json(Seq.empty)
        )
    }
  }

  private def load(orgId: String, groupId: Option[ObjectId])(implicit configuration: OrganizationConfiguration): String = {
    val resourceRoles = Role.allPrimaryRoles(configuration).filterNot(_.resourceType.isEmpty)
    val defaultGroupViewModel = GroupViewModel(
      roleKey = Role.allPrimaryRoles(configuration).head.key,
      rolesWithResources = resourceRoles.map(_.key),
      rolesWithResourceAdmin = Role.allPrimaryRoles(configuration).filter(_.isResourceAdmin).map(_.key),
      rolesResourceType = resourceRoles.map(r => RoleResourceType(r.key, r.resourceType.get.resourceType, Messages("accessControl.resourceType." + r.resourceType.get.resourceType)))
    )

    groupId.flatMap(Group.dao.findOneById(_)) match {
      case None => JJson.generate(defaultGroupViewModel)
      case Some(group) => JJson.generate(
        GroupViewModel(
          id = Some(group._id),
          name = group.name,
          roleKey = group.roleKey,
          canChangeGrantType = group.roleKey != Role.OWN.key,
          users = group.users.map(u => Token(u, u)),
          resources = group.resources.map(r => Token(r.getResourceKey, r.getResourceKey, Some(r.getResourceType.resourceType))),
          rolesWithResources = defaultGroupViewModel.rolesWithResources,
          rolesWithResourceAdmin = defaultGroupViewModel.rolesWithResourceAdmin,
          rolesResourceType = defaultGroupViewModel.rolesResourceType
        )
      )
    }
  }

  private def canUpdateGroup(orgId: String, groupId: ObjectId)(implicit request: RequestHeader): Boolean = {
    groupId != null && organizationServiceLocator.byDomain.isAdmin(orgId, userName)
  }

  private def canCreateGroup(orgId: String)(implicit request: RequestHeader): Boolean =
    organizationServiceLocator.byDomain.isAdmin(orgId, userName)
}

case class GroupViewModel(id: Option[ObjectId] = None,
  name: String = "",
  roleKey: String,
  canChangeGrantType: Boolean = true,
  users: Seq[Token] = Seq.empty[Token],
  resources: Seq[Token] = Seq.empty[Token],
  rolesWithResources: Seq[String] = Seq.empty,
  rolesWithResourceAdmin: Seq[String] = Seq.empty,
  rolesResourceType: Seq[RoleResourceType] = Seq.empty)

case class RoleResourceType(roleKey: String, resourceType: String, resourceTypeName: String)

object GroupViewModel {

  // ~~~ Form utilities
  import extensions.Formatters._

  val tokenListMapping = seq(
    play.api.data.Forms.mapping(
      "id" -> text,
      "name" -> text,
      "tokenType" -> optional(text),
      "data" -> optional(of[Map[String, String]])
    )(Token.apply)(Token.unapply)
  )

  val groupForm: Form[GroupViewModel] = Form(
    mapping(
      "id" -> optional(of[ObjectId]),
      "name" -> nonEmptyText,
      "roleKey" -> nonEmptyText,
      "canChangeGrantType" -> boolean,
      "users" -> tokenListMapping,
      "resources" -> tokenListMapping,
      "rolesWithResources" -> seq(nonEmptyText),
      "rolesWithResourceAdmin" -> seq(nonEmptyText),
      "rolesResourceType" -> seq(
        mapping(
          "roleKey" -> nonEmptyText,
          "resourceType" -> nonEmptyText,
          "resourceTypeName" -> nonEmptyText
        )(RoleResourceType.apply)(RoleResourceType.unapply)
      )
    )(GroupViewModel.apply)(GroupViewModel.unapply)
  )

}

case class GroupListModel(id: String, name: String, size: Int, description: String)