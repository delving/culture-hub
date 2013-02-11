package models

import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO
import HubMongoContext._
import play.api.Play
import play.api.Play.current

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class HubUser(_id: ObjectId = new ObjectId,
    userName: String, // userName, unique in the world
    firstName: String,
    lastName: String,
    email: String,
    userProfile: UserProfile,
    groups: List[ObjectId] = List.empty[ObjectId], // groups this user belongs to
    organizations: List[String] = List.empty[String], // organizations this user belongs to
    accessToken: Option[AccessToken] = None,
    refreshToken: Option[String] = None) {

  val fullname = firstName + " " + lastName

  override def toString = email
}

/** OAuth2 Access token **/
case class AccessToken(token: String, issueTime: Long = System.currentTimeMillis())

object HubUser extends MultiModel[HubUser, HubUserDAO] {

  protected def connectionName: String = "Users"

  protected def initIndexes(collection: MongoCollection) {
    collection.ensureIndex(MongoDBObject("userName" -> 1, "isActive" -> 1))
  }

  protected def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: OrganizationConfiguration): HubUserDAO = new HubUserDAO(collection)

  // ~~~ OAuth 2

  val OAUTH2_TOKEN_TIMEOUT = 3600

  def isValidToken(token: String)(implicit configuration: OrganizationConfiguration): Boolean = {
    if ((Play.isTest || Play.isDev) && token == "TEST") return true
    HubUser.dao.isValidAccessToken(token, OAUTH2_TOKEN_TIMEOUT)
  }

  def getUserByToken(token: String)(implicit configuration: OrganizationConfiguration) = HubUser.dao.findByAccessToken(token)

}

class HubUserDAO(collection: MongoCollection) extends SalatDAO[HubUser, ObjectId](collection) with Pager[HubUser] {

  def findAll = find(MongoDBObject())

  def findByUsername(userName: String, active: Boolean = true): Option[HubUser] = findOne(MongoDBObject("userName" -> userName))

  def findOneByEmail(email: String): Option[HubUser] = findOne(MongoDBObject("email" -> email))

  // ~~~ organizations

  def addToOrganization(userName: String, orgId: String): Boolean = {
    try {
      update(MongoDBObject("userName" -> userName), $addToSet("organizations" -> orgId), false, false, WriteConcern.Safe)
    } catch {
      case t: Throwable => return false
    }
    true
  }

  def removeFromOrganization(userName: String, orgId: String)(implicit configuration: OrganizationConfiguration): Boolean = {
    try {
      update(MongoDBObject("userName" -> userName), $pull("organizations" -> orgId), false, false, WriteConcern.Safe)

      // remove from all groups
      Group.dao.findDirectMemberships(userName).foreach {
        group => Group.dao.removeUser(orgId, userName, group._id)
      }
    } catch {
      case t: Throwable => return false
    }
    true
  }

  def listOrganizationMembers(orgId: String): List[String] = {
    find(MongoDBObject("organizations" -> orgId)).map(_.userName).toList
  }

  // ~~~ various

  def updateProfile(userName: String, firstName: String, lastName: String, email: String, profile: UserProfile) {
    findByUsername(userName).map {
      u =>
        {
          val updated = u.copy(firstName = firstName, lastName = lastName, email = email, userProfile = profile)
          save(updated)
        }
    }
  }

  // ~~~ OAuth2

  def setOauthTokens(user: HubUser, accessToken: String, refreshToken: String) {
    update(MongoDBObject("userName" -> user.userName), _grater.asDBObject(user.copy(accessToken = Some(AccessToken(token = accessToken)), refreshToken = Some(refreshToken))))
  }

  def isValidAccessToken(token: String, timeout: Long = 3600): Boolean = {
    val delta = System.currentTimeMillis() - timeout * 1000
    count(MongoDBObject("accessToken.token" -> token, "accessToken.issueTime" -> MongoDBObject("$gt" -> delta))) > 0
  }

  def findByAccessToken(token: String): Option[HubUser] = {
    if ((Play.isTest || Play.isDev) && token == "TEST") return findOne(MongoDBObject("userName" -> "bob"))
    findOne(MongoDBObject("accessToken.token" -> token))
  }

  def findByRefreshToken(token: String): Option[HubUser] = {
    findOne(MongoDBObject("refreshToken" -> token))
  }

  def evictExpiredAccessTokens(timeout: Long = 3600) {
    val delta = System.currentTimeMillis() - timeout * 1000
    update("accessToken.issueTime" $lt delta, MongoDBObject("$unset" -> MongoDBObject("accessToken" -> 1)))
  }

}
