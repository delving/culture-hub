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
  protected def getAccessField: String = "access"

  private def users(postfix: String = ""): String = if (!postfix.isEmpty) getAccessField + ".users." + postfix else getAccessField + ".users"
  private def id(username: String, node: String) = username + "#" + node
  private def userRightQuery(userId: String, right: String) = MongoDBObject(users() -> MongoDBObject("$elemMatch" -> MongoDBObject("user.id" -> userId, right -> "true")))


  def findAccessRight(username: String, node: String): Option[AccessRight] = {
    val userId: String = id(username, node)
    val query = MongoDBObject(users("user.id") -> userId)
    val dbo = getCollection.findOne(query).getOrElse(return None)
    Option(grater[AccessRight].asObject(dbo))
  }

  def canCreate(username: String, node:String) = getCollection.count(userRightQuery(id(username, node), "create")) > 0
  def canRead(username: String, node:String) = getCollection.count(userRightQuery(id(username, node), "read")) > 0
  def canUpdate(username: String, node:String) = getCollection.count(userRightQuery(id(username, node), "update")) > 0
  def canDelete(username: String, node:String) = getCollection.count(userRightQuery(id(username, node), "delete")) > 0
  def owns(username: String, node:String) = getCollection.count(userRightQuery(id(username, node), "owner")) > 0

  def addAccessRight(username: String, node: String, rights: (String, Boolean)*) {
    val userId: String = id(username, node)
    val query = MongoDBObject(users("user.id") -> userId)

    // TODO FIXME this needs to do some kind of $push I suppose
    val data = MongoDBObject("access" -> MongoDBObject("users" -> MongoDBObject("user" -> MongoDBObject("username" -> username, "node" -> node, "id" -> userId))))

    def rightObject(right: String, value: Boolean) = MongoDBObject("access" -> MongoDBObject("users" -> MongoDBObject("user" -> MongoDBObject(right -> value.toString))))

    rights foreach {
      r => rightObject(r._1, r._2)
    }

    getCollection.update(query, data, true, false, new WriteConcern())
  }

}

/**Access Rights of an object **/
case class AccessRight(users: List[UserAction], groups: List[Group])

/**A User and his rights **/
case class UserAction(user: UserReference,
                      create: Option[Boolean] = Some(false),
                      read: Option[Boolean] = Some(false),
                      update: Option[Boolean] = Some(false),
                      delete: Option[Boolean] = Some(false),
                      owner: Option[Boolean] = Some(false)
                     )

/**A group and its rights **/
case class Group(
                        user: UserReference,
                        name: String,
                        users: List[UserReference],
                        create: Option[Boolean] = Some(false),
                        read: Option[Boolean] = Some(false),
                        update: Option[Boolean] = Some(false),
                        delete: Option[Boolean] = Some(false),
                        owner: Option[Boolean] = Some(false)
                        )

/**An organization, yet to be defined further **/
case class Organization(name: String,
                        public: Boolean,
                        groups: List[Group]
                        )
