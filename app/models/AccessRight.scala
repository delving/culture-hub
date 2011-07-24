package models

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoCollection
import com.novus.salat.grater
import salatContext._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait AccessControl {

  protected def getCollection: MongoCollection

  protected def getObjectQuery(id: AnyRef): MongoDBObject

  protected def getAccessField: String = "access"

  private def users(postfix: String = ""): String = if (!postfix.isEmpty) getAccessField + ".users." + postfix else getAccessField + ".users"

  private def buildId(username: String, node: String) = username + "#" + node

  private def userRightQuery(id: AnyRef, username: String, node: String, right: String) = getObjectQuery(id) ++ MongoDBObject("%s.users.%s.%s".format(getAccessField, buildId(username, node), right) -> true)


  def canCreate(id: AnyRef, username: String, node: String) = getCollection.count(userRightQuery(id, username, node, "create")) > 0

  def canRead(id: AnyRef, username: String, node: String) = getCollection.count(userRightQuery(id, username, node, "read")) > 0

  def canUpdate(id: AnyRef, username: String, node: String) = getCollection.count(userRightQuery(id, username, node, "update")) > 0

  def canDelete(id: AnyRef, username: String, node: String) = getCollection.count(userRightQuery(id, username, node, "delete")) > 0

  def owns(id: AnyRef, username: String, node: String) = getCollection.count(userRightQuery(id, username, node, "owner")) > 0

  def addAccessRight(id: AnyRef, username: String, node: String, rights: (String, Boolean)*) {
    val userId: String = buildId(username, node)
    val query: MongoDBObject = getObjectQuery(id)

    if (getCollection.count(query) > 0) {
      val actionQuery = query ++ MongoDBObject((users(userId) -> MongoDBObject("$exists" -> true)))
      val path: String = "%s.users.%s".format(getAccessField, buildId(username, node))
      val maybeAction = getCollection.findOne(actionQuery, MongoDBObject(path -> 1))
      val action = maybeAction match {
        case Some(action) => action.getAs[DBObject](getAccessField).get.getAs[DBObject]("users").get.getAs[DBObject](userId).get
        case None => grater[UserAction].asDBObject(UserAction(UserReference(username, node, userId)))
      }
      rights foreach {
        right => right._1 match {
          case "create" | "read" | "update" | "delete" | "owner" => action.put(right._1, right._2)
          case _ => throw new RuntimeException("Invalid access right '%s'" format (right._1))
        }
      }
      val updated = MongoDBObject("$set" -> MongoDBObject("%s.users.%s".format(getAccessField, userId) -> action))
      getCollection.update(query, updated, true, false, new WriteConcern())
    }
  }
}


/**Access Rights of an object **/
case class AccessRight(users: Map[String, UserAction] = Map.empty[String, UserAction], groups: List[Group] = List.empty[Group])

/**A User and his rights **/
case class UserAction(user: UserReference = UserReference("", "", ""),
                      create: Option[Boolean] = Some(false),
                      read: Option[Boolean] = Some(false),
                      update: Option[Boolean] = Some(false),
                      delete: Option[Boolean] = Some(false),
                      owner: Option[Boolean] = Some(false))

/**A group and its rights **/
case class Group(
                        user: UserReference,
                        name: String,
                        users: List[UserReference],
                        create: Option[Boolean] = Some(false),
                        read: Option[Boolean] = Some(false),
                        update: Option[Boolean] = Some(false),
                        delete: Option[Boolean] = Some(false),
                        owner: Option[Boolean] = Some(false))

/**An organization, yet to be defined further **/
case class Organization(name: String,
                        public: Boolean,
                        groups: List[Group])
