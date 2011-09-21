package controllers.user

import play.templates.Html
import play.mvc.Before
import controllers.{UserAuthentication, Secure, DelvingController}
import play.mvc.results.Result
import com.mongodb.casbah.query.Imports
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import models._
import com.mongodb.WriteConcern
import extensions.CHJson._
import com.novus.salat.dao.SalatDAOUpdateError

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends DelvingController with UserSecured {

  import views.User.Admin._

  def index: Html = {
    html.index()
  }

  def groupList: Html = {
    val userGroups = UserGroup.findByUser(connectedUserId)
    html.groupList(groups = userGroups)
  }

  def groupUpdate(id: String): Html = {
    html.group(Option(id))
  }

  def groupLoad(user: String, id: String): Result = {



    def makeMembers(users: Map[String, UserReference]): Seq[Member] = (for (u: UserReference <- users.values) yield {
      val user = User.findOne(MongoDBObject("reference.id" -> u.id)) // MongoDBObject("reference.id" -> 1, "firstName" -> 1, "lastName" -> 1)
      Member(u.id, user.get.fullname)
    }).toList

    def getRepositories(group: String) = for(repo <- UserGroup.getRepositories(group + "#" + user + "#" + getNode)) yield Repository(repo._id.toString, repo.name)

    // load by name and user
    val maybeGroup: Option[UserGroup] = UserGroup.findById(id) // , "user" -> MongoDBObject("id" -> ref.id, "node" -> ref.node, "username" -> ref.username)
    maybeGroup match {
      case Some(group) => {
        val groupModel = GroupModel(Some(group._id), group.name, group.read, group.update, group.delete, makeMembers(group.users), getRepositories(group.name))
        Json(groupModel)
      }
      case None => Json(GroupModel(None, "", Some(false), Some(false), Some(false), List(), List()))
    }
  }


  def groupSubmit(data: String): Result = {

    val group: GroupModel = parse[GroupModel](data)
    val userGroup = UserGroup(user = connectedUserId, name = group.name, users = group.getUsers, read = group.readRight, update = group.updateRight, delete = group.deleteRight, owner = Some(false))

//    val repositories = group.getRepositories

//    for(dataSet <- DataSet.findAllByOwner(getUserReference)) {
//      val traversedHasGroup: Boolean = dataSet.access.groups.contains(userGroup._id) // TODO change type of access.groups
//      val updatedHasGroup: Boolean = !repositories.filter(r => r._id == dataSet._id).isEmpty
//
//      val newGroups =
//        if(traversedHasGroup && !updatedHasGroup) {
//          // was removed
//          Some(dataSet.access.groups.filterNot(_ == userGroup._id)) // TODO change type of access.groups
//      } else if(!traversedHasGroup && updatedHasGroup) {
//          // was added
//          Some(dataSet.access.groups ++ List(userGroup._id))
//      } else {
//          // nothing changed
//          None
//      }
//
//      newGroups.foreach(g => DataSet.updateGroups(dataSet, g))
//    }

    val persistedGroup: Option[GroupModel] = group.id match {
      case None => {
        // new guy
        val inserted: Option[Imports.ObjectId] = UserGroup.insert(userGroup)
        if(inserted != None) Some(group.copy(id = inserted)) else None
      }
      case Some(id) => {
        // updated guy
        val existingGroup = UserGroup.findOneByID(id)
        if(existingGroup == None) Error("UserGroup with id %s does not exist".format(id))
        val updatedGroup = existingGroup.get.copy(name = userGroup.name, users = userGroup.users, read = userGroup.read, update = userGroup.update, delete = userGroup.delete, owner = userGroup.owner)
        try {
          UserGroup.update(MongoDBObject("_id" -> id), updatedGroup, false, false, new WriteConcern())
          Some(group)
        } catch {
          case e: SalatDAOUpdateError => None
          case _ => None
        }
      }
    }

    persistedGroup match {
      case Some(theGroup) => Json(theGroup)
      case None => Error("UserGroup could not be saved")
    }
  }

}

case class GroupModel(id: Option[ObjectId] = None, name: String, readRight: Option[Boolean] = Some(false), updateRight: Option[Boolean] = Some(false), deleteRight: Option[Boolean] = Some(false), members: Seq[Member], repositories: Seq[Repository]) {

  def getUsers: Map[String, UserReference] = (for (m: Member <- members) yield {
    val ref: Array[String] = m.id.split('#')
    (m.id, UserReference(ref(0), ref(1), m.id))
  }).toMap

  def getRepositories: List[models.Repository] = {
    val ids = for (repo <- repositories) yield new ObjectId(repo.id)
    DataSet.find(MongoDBObject("_id" -> MongoDBObject("$in" -> ids))).toList
  }


}

case class Member(id: String, name: String)

case class Repository(id: String, name: String)