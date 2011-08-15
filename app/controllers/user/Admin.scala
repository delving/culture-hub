package controllers.user

import play.templates.Html
import play.mvc.Before
import controllers.{UserAuthentication, Secure, DelvingController}
import play.mvc.results.Result
import com.mongodb.casbah.query.Imports
import extensions.{RenderLiftJson, LiftJson}
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.WriteConcern
import models.{User, UserReference, UserGroup}
import org.bson.types.ObjectId

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends DelvingController with UserAuthentication with Secure with LiftJson {

  import views.User.Admin._

  @Before def checkUser(): Result = {
    if (connectedUser == null || connectedUser != params.get("user")) {
      return Forbidden("You do not have access here")
    }
    Continue
  }

  def index: Html = {
    html.index()
  }

  def groupList: Html = {
    val userGroups = UserGroup.findByUser(getUserId(connectedUser))
    html.groupList(groups = userGroups)
  }

  def groupUpdate(name: String): Html = {
    html.group(name)
  }

  // Optional fields with custom types are BROKEN with lift-json 2.4-M3 (and any other version)
  // see if someone provides a fix or workaround here:
  // http://groups.google.com/group/liftweb/browse_thread/thread/ed532423bbd908eb#
  private val dummyObjectId = new ObjectId("47cc67093475061e3d95369d")

  case class GroupModel(_id: Option[ObjectId] = None, name: String, readRight: Option[Boolean] = Some(false), updateRight: Option[Boolean] = Some(false), deleteRight: Option[Boolean] = Some(false), members: Seq[Member])

  case class Member(id: String, name: String)

  def groupLoad(user: String, name: String): Result = {

    def makeMembers(users: Map[String, UserReference]): Seq[Member] = (for (u: UserReference <- users.values) yield {
      val user = User.findOne(MongoDBObject("reference.id" -> u.id)) // MongoDBObject("reference.id" -> 1, "firstName" -> 1, "lastName" -> 1)
      Member(u.id, user.get.fullname)
    }).toList

    // load by name and user
    val ref = getUserReference
    val maybeGroup: Option[UserGroup] = UserGroup.findOne(MongoDBObject("name" -> name)) // , "user" -> MongoDBObject("id" -> ref.id, "node" -> ref.node, "username" -> ref.username)
    maybeGroup match {
      case Some(group) => {
        val groupModel = GroupModel(Some(group._id), group.name, group.read, group.update, group.delete, makeMembers(group.users))
        RenderLiftJson(groupModel)
      }
      case None => RenderLiftJson(GroupModel(Some(dummyObjectId), "", Some(false), Some(false), Some(false), List()))
    }

  }

  def groupSubmit(data: String): Result = {

    // /!\ warning:
    // lift-json is fragile to say the least. it also is one of the few libraries that exist out there and that works without having to do insane amounts of mambo-jumbo to get things to work
    // with custom types (such as a BSON ObjectId)
    // if the json string that you want to deserialize into a case class does not contain *all* of the parameters necessary for the construction of the case class, lift-json
    // will fail silently and leave you with an instance where all members are null, None, or empty. so make sure you always provide everything or madness will ensue.

    def makeUsers(group: GroupModel): Map[String, UserReference] = (for (m: Member <- group.members) yield {
      val ref: Array[String] = m.id.split('#')
      (m.id, UserReference(ref(0), ref(1), m.id))
    }).toMap


    val group: GroupModel = in[GroupModel](data)
    val userGroup = UserGroup(user = getUserReference, name = group.name, users = makeUsers(group), read = group.readRight, update = group.updateRight, delete = group.deleteRight, owner = Some(false))

    val persistedGroup = group._id match {
      case Some(id) if id == dummyObjectId => {
        // new guy
        val inserted: Option[Imports.ObjectId] = UserGroup.insert(userGroup)
        // TODO handle the case where inserted == None, i.e. something went wrong on the backend.
        group.copy(_id = inserted)
      }
      case Some(id) => {
        // updated guy
        // TODO handle the case when something goes wrong on the backend
        // TODO for cases where we update only some fields, we need to do a merge of the persisted document and the changed field values by updated only those fields that we are touching in the view
        UserGroup.update(MongoDBObject("_id" -> id), userGroup, false, false, new WriteConcern())
        group
      }
    }
    RenderLiftJson(persistedGroup)
  }

}