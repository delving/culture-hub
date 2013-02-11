package core.services

import _root_.core._
import _root_.core.node._
import scala.collection.mutable.{ ArrayBuffer, HashMap }
import extensions.MissingLibs
import play.api.Play
import play.api.Play.current
import eu.delving.definitions.OrganizationEntry

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class MemoryServices(val users: HashMap[String, MemoryUser] = new HashMap[String, MemoryUser],
  val organizations: HashMap[String, MemoryOrganization] = new HashMap[String, MemoryOrganization],
  val nodes: ArrayBuffer[MemoryNode] = new ArrayBuffer[MemoryNode]) extends AuthenticationService with RegistrationService with UserProfileService
    with OrganizationService with DirectoryService with NodeRegistrationService
    with NodeDirectoryService {

  // ~~~ authentication

  def connect(userName: String, password: String): Boolean = users.get(userName).getOrElse(return false).password == password

  // ~~~ registration

  def isUserNameTaken(userName: String) = users.contains(userName)

  def isEmailTaken(email: String) = users.values.exists(_.email == email)

  def isAccountActive(email: String): Boolean = users.values.find(_.email == email).getOrElse(return false).isActive

  def registerUser(userName: String, node: String, firstName: String, lastName: String, email: String, password: String): Option[String] = {
    if (users.contains(userName)) return None
    val newUser = MemoryUser(userName, firstName, lastName, email, password, models.UserProfile(), false, if (Play.isTest) "TESTACTIVATION" else MissingLibs.UUID)
    users += (userName -> newUser)
    Some(newUser.activationToken)
  }

  def activateUser(activationToken: String) = {
    users.values.find(_.activationToken == activationToken).map { u =>
      users.put(u.userName, u.copy(isActive = true))
      Some(RegisteredUser(u.userName, u.firstName, u.lastName, u.email))
    }.getOrElse(None)
  }

  def preparePasswordReset(email: String) = users.values.find(_.email == email).map {
    user: MemoryUser =>
      {
        val resetUser = user.copy(passwordResetToken = Some(MissingLibs.UUID))
        users += (resetUser.userName -> resetUser)
        resetUser.passwordResetToken
      }
  }.getOrElse(None)

  def resetPassword(resetToken: String, newPassword: String): Boolean = {
    val resetUser = users.values.find(u => u.passwordResetToken.isDefined && u.passwordResetToken.get == resetToken).getOrElse(return false).copy(password = newPassword, passwordResetToken = None)
    users += (resetUser.userName -> resetUser)
    true
  }

  // ~~~ user profile

  def getUserProfile(userName: String) = users.get(userName).map {
    u =>
      {
        val p = u.profile
        UserProfile(p.isPublic, u.firstName, u.lastName, u.email, p.fixedPhone, p.description, p.funFact, p.websites, p.twitter, p.linkedIn)
      }
  }

  def updateUserProfile(userName: String, profile: UserProfile): Boolean = {
    val user = users.get(userName).getOrElse(return false).copy(profile = models.UserProfile(profile.isPublic, profile.fixedPhone, profile.description, profile.funFact, profile.websites, profile.twitter, None, profile.linkedIn))
    users += (userName -> user)
    true
  }

  // ~~~ organization

  def exists(orgId: String) = organizations.contains(orgId)

  def queryByOrgId(query: String): Seq[OrganizationProfile] = organizations.values.filter(_.orgId.contains(query)).map { org =>
    OrganizationProfile(org.orgId, org.name)
  }.toSeq

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

  // node registration

  def findNode(n: Node, node: Node) = n.orgId == node.orgId && n.nodeId == node.nodeId

  def registerNode(node: Node, userName: String) {
    nodes.append(MemoryNode(node))
  }

  def updateNode(node: Node) {
    nodes.find(findNode(_, node)).map { n =>
      val updated = n.copy(name = node.name)
      removeNode(node)
      nodes.append(updated)
    }
  }

  def removeNode(node: Node) {
    val i = nodes.indexWhere(findNode(_, node))
    if (nodes.isDefinedAt(i)) {
      nodes.remove(i)
    }
  }

  def listMembers(node: Node): Seq[String] = nodes.find(findNode(_, node)).map(_.members).getOrElse(Seq.empty)

  def addMember(node: Node, userName: String) {
    nodes.find(findNode(_, node)).map { n =>
      val updated = n.copy(members = n.members ++ Seq(userName))
      removeNode(node)
      nodes.append(updated)
    }
  }

  def removeMember(node: Node, userName: String) {
    nodes.find(findNode(_, node)).map { n =>
      val updated = n.copy(members = n.members.filterNot(_ == userName))
      removeNode(node)
      nodes.append(updated)
    }
  }

  // node directory

  def listEntries: Seq[Node] = {
    nodes.toSeq
  }

  def findOneById(nodeId: String): Option[Node] = nodes.find(_.nodeId == nodeId)

  // directory

  private val dummyDelving = OrganizationEntry("http://id.delving.org/org/1", "Delving", "NL")

  def findOrganization(query: String): List[OrganizationEntry] = List(dummyDelving)

  def findOrganizationByName(name: String): Option[OrganizationEntry] = if (name.toLowerCase == "delving") Some(dummyDelving) else None
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

case class MemoryNode(orgId: String,
    nodeId: String,
    name: String,
    members: Seq[String]) extends Node {

  def isLocal: Boolean = true
}

object MemoryNode {

  def apply(n: Node): MemoryNode = MemoryNode(n.orgId, n.nodeId, n.name, Seq.empty)
}