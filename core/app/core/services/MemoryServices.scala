package core.services

import core._
import collection.mutable.HashMap
import extensions.MissingLibs
import eu.delving.definitions.OrganizationEntry


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class MemoryServices(val users: HashMap[String, MemoryUser] = new HashMap[String, MemoryUser],
                     val organizations: HashMap[String, MemoryOrganization] = new HashMap[String, MemoryOrganization]
                    ) extends AuthenticationService with RegistrationService with UserProfileService with OrganizationService with DirectoryService {


  // ~~~ authentication

  def connect(userName: String, password: String): Boolean = users.get(userName).getOrElse(return false).password == password

  // ~~~ registration

  def isUserNameTaken(userName: String) = users.contains(userName)

  def isEmailTaken(email: String) = users.values.exists(_.email == email)

  def isAccountActive(email: String): Boolean = users.values.find(_.email == email).getOrElse(return false).isActive

  def registerUser(userName: String, node: String, firstName: String, lastName: String, email: String, password: String): Option[String] = {
    if (users.contains(userName)) return None
    val newUser = MemoryUser(userName, firstName, lastName, email, password, models.UserProfile())
    users += (userName -> newUser)
    Some(newUser.activationToken)
  }

  def activateUser(activationToken: String) = {
    users.values.find(_.activationToken == activationToken).map {
      u => Some(RegisteredUser(u.userName, u.firstName, u.lastName, u.email))
    }.getOrElse(None)
  }

  def preparePasswordReset(email: String) = users.values.find(_.email == email).map {
    user: MemoryUser => {
      val resetUser = user.copy(passwordResetToken = Some(MissingLibs.UUID))
      users += (resetUser.userName -> resetUser)
      resetUser.passwordResetToken
    }
  }.getOrElse(None)

  def resetPassword(resetToken: String, newPassword: String): Boolean = {
    val resetUser = users.values.find(_.passwordResetToken == resetToken).getOrElse(return false).copy(password = newPassword, passwordResetToken = None)
    users += (resetUser.userName -> resetUser)
    true
  }

  // ~~~ user profile

  def getUserProfile(userName: String) = users.get(userName).map {
    u => {
      val p = u.profile
      UserProfile(p.isPublic, u.firstName, u.lastName, u.email, p.description, p.funFact, p.websites, p.twitter, p.linkedIn)
    }
  }

  def updateUserProfile(userName: String, profile: UserProfile): Boolean = {
    val user = users.get(userName).getOrElse(return false).copy(profile = models.UserProfile(profile.isPublic, profile.description, profile.funFact, profile.websites, profile.twitter, None, profile.linkedIn))
    users += (userName -> user)
    true
  }

  // ~~~ organization

  def exists(orgId: String) = organizations.contains(orgId)

  def isAdmin(orgId: String, userName: String): Boolean = organizations.get(orgId).getOrElse(return false).admins.contains(userName)

  def listAdmins(orgId: String): List[String] = organizations.get(orgId).getOrElse(return List.empty).admins

  def addAdmin(orgId: String, userName: String): Boolean = {
    val org = organizations.get(orgId).getOrElse(return false)
    organizations += (orgId -> org.copy(admins = (org.admins ::: List(userName))))
    true
  }

  def removeAdmin(orgId: String, userName: String): Boolean = {
    val org = organizations.get(orgId).getOrElse(return false)
    organizations += (orgId -> org.copy(admins = (org.admins.filterNot(_ == userName))))
    true
  }

  def getName(orgId: String, language: String): Option[String] = organizations.get(orgId).getOrElse(return None).name.get(language)

  // directory
  private val dummyDelving = OrganizationEntry("http://dummy.org/delving", "Delving", "NL")

  def findOrganization(query: String): List[OrganizationEntry] = List(dummyDelving)

  def findOrganizationByName(name: String): Option[OrganizationEntry] = if(name.toLowerCase == "delving") Some(dummyDelving) else None
}

case class MemoryUser(userName: String,
                      firstName: String,
                      lastName: String,
                      email: String,
                      password: String,
                      profile: models.UserProfile,
                      isActive: Boolean = false,
                      activationToken: String = MissingLibs.UUID,
                      passwordResetToken: Option[String] = None)

case class MemoryOrganization(orgId: String,
                              name: Map[String, String],
                              admins: List[String])

