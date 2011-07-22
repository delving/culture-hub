package models

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoCollection

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait AccessControl {

  protected def getCollection: MongoCollection
  protected def getAccessField: String = "access"

  private def user(postfix: String): String = getAccessField + ".users." + postfix
  private def id(username: String, node: String) = username + "#" + node

  def canCreate(username: String, node:String) = {
    val userId: String = id(username, node)
    val query = MongoDBObject(user("user.id") -> userId, user("create") -> "true")
    getCollection.count(query) > 0
  }

  def canRead(username: String, node:String) = {
    val userId: String = id(username, node)
    val query = MongoDBObject(user("user.id") -> userId, user("read") -> "true")
    getCollection.count(query) > 0
  }

  def canUpdate(username: String, node:String) = {
    val userId: String = id(username, node)
    val query = MongoDBObject(user("user.id") -> userId, user("update") -> "true")
    getCollection.count(query) > 0
  }

  def canDelete(username: String, node:String) = {
    val userId: String = id(username, node)
    val query = MongoDBObject(user("user.id") -> userId, user("delete") -> "true")
    getCollection.count(query) > 0
  }

  def owns(username: String, node:String) = {
    val userId: String = id(username, node)
    val query = MongoDBObject(user("user.id") -> userId, user("owner") -> "true")
    getCollection.count(query) > 0
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
