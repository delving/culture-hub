package core


/**
 * Managing a user profile (updating information)
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait UserProfileService {

  def getUserProfile(userName: String): Option[UserProfile]

  def updateUserProfile(userName: String, profile: UserProfile): Boolean

  def listOrganizations(userName: String) : List[String]

}


case class UserProfile(isPublic:      Boolean = false,
                       firstName:     String,
                       lastName:      String,
                       email:         String,
                       description:   Option[String] = None,
                       funFact:       Option[String] = None,
                       websites:      List[String] = List.empty,
                       twitter:       Option[String] = None,
                       linkedIn:      Option[String] = None
                      )