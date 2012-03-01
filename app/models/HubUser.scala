package models

import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO
import mongoContext._

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

object HubUser extends SalatDAO[HubUser, ObjectId](hubUserCollection) with Pager[HubUser] {

  def findByUsername(userName: String, active: Boolean = true): Option[HubUser] = HubUser.findOne(MongoDBObject("userName" -> userName))

  def findBookmarksCollection(userName: String): Option[UserCollection] = {
    UserCollection.findOne(MongoDBObject("isBookmarksCollection" -> true, "userName" -> userName))
  }


}
