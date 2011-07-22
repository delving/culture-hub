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

  private def users(postfix: String): String = getAccessField + ".users." + postfix
  private def id(username: String, node: String) = username + "#" + node

  def findAccessRight(username: String, node: String): Option[AccessRight] = {
    val userId: String = id(username, node)
    val query = MongoDBObject(users("user.id") -> userId)
    val dbo = getCollection.findOne(query).getOrElse(return None)
    Option(grater[AccessRight].asObject(dbo))
  }


  def canCreate(username: String, node:String) = {
    val userId: String = id(username, node)
    val query = MongoDBObject(users("user.id") -> userId, users("create") -> "true")
    getCollection.count(query) > 0
  }

  def canRead(username: String, node:String) = {
    val userId: String = id(username, node)
    val query = MongoDBObject(users("user.id") -> userId, users("read") -> "true")
    getCollection.count(query) > 0
  }

  def canUpdate(username: String, node:String) = {
    val userId: String = id(username, node)
    val query = MongoDBObject(users("user.id") -> userId, users("update") -> "true")
    getCollection.count(query) > 0
  }

  def canDelete(username: String, node:String) = {
    val userId: String = id(username, node)
    val query = MongoDBObject(users("user.id") -> userId, users("delete") -> "true")
    getCollection.count(query) > 0
  }

  def owns(username: String, node:String) = {
    val userId: String = id(username, node)
    val query = MongoDBObject(users("user.id") -> userId, users("owner") -> "true")
    getCollection.count(query) > 0
  }

  def addAccessRight(username: String, node: String, create: Boolean = false, read: Boolean = false, update: Boolean = false, delete: Boolean = false, owns: Boolean = false) {
    val userId: String = id(username, node)
    val query = MongoDBObject(users("user.id") -> userId)
    val data = MongoDBObject(
      users("user") -> MongoDBObject("username" -> username, "node" -> node, "id" -> userId),
      users("create") -> create,
      users("read") -> read,
      users("update") -> update,
      users("delete") -> delete,
      users("owner") -> owns
    )
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
