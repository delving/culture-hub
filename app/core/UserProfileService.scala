package core

/**
 * Managing a user profile (updating information)
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait UserProfileService {

  def getUserProfile(userName: String): Option[UserProfile]

  def updateUserProfile(userName: String, profile: UserProfile): Boolean

}

case class UserProfile(isPublic:      Boolean,
                       firstName:     String,
                       lastName:      String,
                       email:         String,
                       description:   Option[String],
                       funFact:       Option[String],
                       websites:      List[String],
                       twitter:       Option[String],
                       linkedIn:      Option[String]
                      )

