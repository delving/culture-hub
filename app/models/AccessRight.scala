package models

import com.mongodb.casbah.Imports._
import com.novus.salat.grater
import salatContext._
import com.mongodb.casbah.{Imports, MongoCollection}
import com.novus.salat.dao.SalatDAO

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait AccessControl {

  protected def getCollection: MongoCollection

  protected def getObjectIdField: String

  protected def getObjectQuery(id: AnyRef): MongoDBObject = MongoDBObject(getObjectIdField -> id.toString)

  protected def getAccessField: String = "access"

  private def users(postfix: String = ""): String = if (!postfix.isEmpty) getAccessField + ".users." + postfix else getAccessField + ".users"

  private def buildId(username: String, node: String) = username + "#" + node

  def canRead(id: AnyRef, username: String, node: String) = hasRight(id, username, node, "read")
  def canRead(id: AnyRef, user: User) = hasRight(id, user.reference.username, user.reference.node, "read")

  def canUpdate(id: AnyRef, username: String, node: String) = hasRight(id, username, node, "update")
  def canUpdate(id: AnyRef, user: User) = hasRight(id, user.reference.username, user.reference.node,  "update")

  def canDelete(id: AnyRef, username: String, node: String) = hasRight(id, username, node, "delete")
  def canDelete(id: AnyRef, user: User) = hasRight(id, user.reference.username, user.reference.node,  "delete")

  def owns(id: AnyRef, username: String, node: String) = hasRight(id, username, node, "owner")
  def owns(id: AnyRef, user: User) = hasRight(id, user.reference.username, user.reference.node, "owner")

  def hasRight(id: AnyRef, username: String, node: String, right: String) : Boolean = hasUserRight(id, username, node, right) || hasGroupRight (id, username, node, right)
  def hasRight(id: AnyRef, right: String, user: User) : Boolean = hasUserRight(id, user.reference.username, user.reference.node, right) || hasGroupRight (id, user.reference.username, user.reference.node, right)

  /** find all objects for which the user has a right for (either by direct access or through a group) **/
  def findAllByRight(username: String, node: String, right: String): Iterator[DBObject] = findUserRightObjects(username, node, right) ++ findGroupRightObjects(username, node, right)

  def findAllByRight(user: UserReference, right: String): Iterator[DBObject] = findAllByRight(user.username, user.node, right)


  private def hasUserRight(id: AnyRef, username: String, node: String, right: String): Boolean = {
    val objectQuery = getObjectQuery(id) ++ userRightQuery(username, node, right)
    getCollection.count(objectQuery) > 0
  }

  private def hasGroupRight(id: AnyRef, username: String, node: String, right: String): Boolean = {
    val query = getObjectQuery(id)
    val access = getCollection.findOne(query, MongoDBObject(getAccessField -> 1)).getOrElse(return false)
    val groups: Imports.DBObject = access.getAs[DBObject](getAccessField).get.getAs[DBObject]("groups").get
    val userPath = "users.%s" format(buildId(username, node))
    val groupQuery = MongoDBObject("id" -> MongoDBObject("$in" -> groups.values.toList), right -> true, userPath -> MongoDBObject("$exists" -> true))
    groupCollection.count(groupQuery) > 0
  }

  private def userRightQuery(username: String, node: String, right: String) =
    MongoDBObject("%s.users.%s.%s".format(getAccessField, buildId(username, node), right) -> true)

  private def findGroupRightObjects(username: String, node: String, right: String) = {
    // find the groups this user is in and have the right we look for
    val userPath = "users.%s" format(buildId(username, node))
    val groupQuery = MongoDBObject(userPath -> MongoDBObject("$exists" -> true), right -> true)
    val groupsWithRight: List[Imports.DBObject] = groupCollection.find(groupQuery, MongoDBObject("id" -> 1)).toList

    // find the records that have this group in it
    getCollection.find(MongoDBObject("%s.groups".format(getAccessField) -> MongoDBObject("$in" -> groupsWithRight)))
  }

  private def findUserRightObjects(username: String, node: String, right: String) = getCollection.find(userRightQuery(username, node, right))


  def addAccessRight(id: AnyRef, username: String, node: String, rights: (String, Boolean)*) {
    val userId: String = buildId(username, node)
    val query: MongoDBObject = getObjectQuery(id)

    if (getCollection.count(query) > 0) {
      val actionQuery = query ++ MongoDBObject((users(userId) -> MongoDBObject("$exists" -> true)))
      val path: String = "%s.users.%s".format(getAccessField, buildId(username, node))
      val maybeAction = getCollection.findOne(actionQuery, MongoDBObject(path -> 1))
      val action = maybeAction match {
        case Some(a) => a.getAs[DBObject](getAccessField).get.getAs[DBObject]("users").get.getAs[DBObject](userId).get
        case None => grater[UserAction].asDBObject(UserAction(UserReference(username, node, userId)))
      }
      rights foreach {
        right => right._1 match {
          case "read" | "update" | "delete" | "owner" => action.put(right._1, right._2)
          case _ => throw new RuntimeException("Invalid access right '%s'" format (right._1))
        }
      }
      val updated = MongoDBObject("$set" -> MongoDBObject("%s.users.%s".format(getAccessField, userId) -> action))
      getCollection.update(query, updated, true, false, new WriteConcern())
    }
  }
}


/**
 * Access Rights of an object
 * users: map of (key, UserAction) where key == "username#node"
 * groups: list of key where key == groupname#username#node
 */
case class AccessRight(users: Map[String, UserAction] = Map.empty[String, UserAction], groups: List[String] = List.empty[String]) {
  def getOwners: List[User] = User.find(MongoDBObject("reference.id" -> MongoDBObject("$in" -> getOwnerIDs))).toList
  def getOwnerIDs: List[String] = (for(uA <- users.values.filter(userAction => userAction.owner == Some(true))) yield uA.user.id).toList
}

/**A User and his rights **/
case class UserAction(user: UserReference = UserReference("", "", ""),
                      read: Option[Boolean] = Some(false),
                      update: Option[Boolean] = Some(false),
                      delete: Option[Boolean] = Some(false),
                      owner: Option[Boolean] = Some(false))

/**A group and its rights **/
case class UserGroup(
                 _id: ObjectId = new ObjectId,
                 id: String, // temporary, name#user#node, decide what to do with this
                 user: UserReference,
                 name: String,
                 users: Map[String, UserReference],
                 read: Option[Boolean] = Some(false),
                 update: Option[Boolean] = Some(false),
                 delete: Option[Boolean] = Some(false),
                 owner: Option[Boolean] = Some(false))

object UserGroup extends SalatDAO[UserGroup, ObjectId](groupCollection) {

  def findByUser(userId: String) = {
    find(MongoDBObject("user.id" -> userId)).toList
  }

  /** all repositories (collections) for this group **/
  def getRepositories(id: String): List[Repository] = DataSet.find(MongoDBObject("access.groups" -> id)).toList

}

/**An organization, yet to be defined further **/
case class Organization(name: String,
                        public: Boolean,
                        groups: List[UserGroup])
