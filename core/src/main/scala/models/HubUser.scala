package models

import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO
import mongoContext._
import play.api.Play
import play.api.Play.current

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class HubUser(_id: ObjectId = new ObjectId,
                userName: String,                                 // userName, unique in the world
                firstName: String,
                lastName: String,
                email: String,
                userProfile: UserProfile,
                groups: List[ObjectId] = List.empty[ObjectId],    // groups this user belongs to
                organizations: List[String] = List.empty[String], // organizations this user belongs to
                accessToken: Option[AccessToken] = None,
                refreshToken: Option[String] = None) {

  val fullname = firstName + " " + lastName

  override def toString = email
}

/** OAuth2 Access token **/
case class AccessToken(token: String, issueTime: Long = System.currentTimeMillis())

object HubUser extends SalatDAO[HubUser, ObjectId](hubUserCollection) with Pager[HubUser] {

  def findAll = find(MongoDBObject())

  def findByUsername(userName: String, active: Boolean = true): Option[HubUser] = HubUser.findOne(MongoDBObject("userName" -> userName))

//  def findBookmarksCollection(userName: String): Option[UserCollection] = {
//    UserCollection.findOne(MongoDBObject("isBookmarksCollection" -> true, "userName" -> userName))
//  }

  // ~~~ organizations

  def addToOrganization(userName: String, orgId: String): Boolean = {
    try {
      HubUser.update(MongoDBObject("userName" -> userName), $addToSet ("organizations" -> orgId), false, false, WriteConcern.Safe)
    } catch {
      case _ => return false
    }
    true
  }

  def removeFromOrganization(userName: String, orgId: String): Boolean = {
    try {
      HubUser.update(MongoDBObject("userName" -> userName), $pull ("organizations" -> orgId), false, false, WriteConcern.Safe)

      // remove from all groups
      Group.findDirectMemberships(userName, orgId).foreach {
        group => Group.removeUser(userName, group._id)
      }
    } catch {
      case _ => return false
    }
    true
  }
  
  def listOrganizationMembers(orgId: String): List[String] = {
    HubUser.find(MongoDBObject("organizations" -> orgId)).map(_.userName).toList
  }
  
  // ~~~ various
  
  def updateProfile(userName: String, firstName: String, lastName: String, email: String, profile: UserProfile) {
    findByUsername(userName).map {
      u => {
        val updated = u.copy(firstName = firstName, lastName = lastName, email = email,  userProfile = profile)
        HubUser.save(updated)
      }
    }
  }

  // ~~~ OAuth2

  def setOauthTokens(user: HubUser, accessToken: String, refreshToken: String) {
    HubUser.update(MongoDBObject("userName" -> user.userName), user.copy(accessToken = Some(AccessToken(token = accessToken)), refreshToken = Some(refreshToken)), false, false, new WriteConcern())
  }

  def isValidAccessToken(token: String, timeout: Long = 3600): Boolean = {
    val delta = System.currentTimeMillis() - timeout * 1000
    HubUser.count(MongoDBObject("accessToken.token" -> token, "accessToken.issueTime" -> MongoDBObject("$gt" -> delta))) > 0
  }

  def findByAccessToken(token: String): Option[HubUser] = {
    if((Play.isTest || Play.isDev) && token == "TEST") return HubUser.findOne(MongoDBObject("userName" -> "bob"))
    HubUser.findOne(MongoDBObject("accessToken.token" -> token))
  }

  def findByRefreshToken(token: String): Option[HubUser] = {
    HubUser.findOne(MongoDBObject("refreshToken" -> token))
  }

  def evictExpiredAccessTokens(timeout: Long = 3600) {
    val delta = System.currentTimeMillis() - timeout * 1000
    HubUser.update("accessToken.issueTime" $lt delta, MongoDBObject("$unset" -> MongoDBObject("accessToken" -> 1)), false, false, new WriteConcern())
  }


}
