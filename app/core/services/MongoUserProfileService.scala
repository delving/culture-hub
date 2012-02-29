package core.services

import models.User
import core.{UserProfile, UserProfileService}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class MongoUserProfileService extends UserProfileService {

  def getUserProfile(userName: String): Option[UserProfile] = {
    User.findByUsername(userName).map {
      user: User =>
        UserProfile(
          firstName = user.firstName,
          lastName = user.lastName,
          email = user.email,
          description = user.userProfile.description,
          funFact = user.userProfile.funFact,
          websites = user.userProfile.websites,
          twitter = user.userProfile.twitter,
          linkedIn = user.userProfile.linkedIn
        )
    }

  }

  def updateUserProfile(userName: String, profile: UserProfile) = {
    User.updateProfile(userName, profile.firstName, profile.lastName, profile.email, models.UserProfile(
      description = profile.description,
      funFact = profile.funFact,
      websites = profile.websites,
      twitter = profile.twitter,
      linkedIn = profile.linkedIn
    ))

  }
}
