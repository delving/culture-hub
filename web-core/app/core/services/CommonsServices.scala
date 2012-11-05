package core.services

import core._
import core.node._
import play.api.libs.Crypto
import play.api.libs.ws.{Response, WS}
import play.api.libs.json._
import play.api.libs.json.util._
import play.api.libs.json.Writes._
import play.api.Logger
import java.util.concurrent.TimeoutException
import java.net.URLEncoder
import extensions.MissingLibs
import eu.delving.definitions.OrganizationEntry
import scala.concurrent.Await
import scala.concurrent.duration._


/**
 * TODO harden this, error handling, logging... for now we always return the worst case scenario in case of an error. however we should make the clients
 * of these services aware of lookup problems
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CommonsServices(commonsHost: String, orgId: String, apiToken: String, node: String) extends AuthenticationService
  with RegistrationService with UserProfileService with OrganizationService with DirectoryService
  with NodeRegistrationService with NodeDirectoryService
  with play.api.http.Status {


  // ~~~ JSON converters

  implicit val OrganizationProfileReads = (
      (__ \ "orgId").read[String] and
      (__ \ "name").read[Map[String, String]]
    )(OrganizationProfile.apply _)

  implicit val OrganizationProfileWrites = (
    (__ \ "orgId").write[String] and
    (__ \ "name").write[Map[String, String]]
  )(unlift(OrganizationProfile.unapply))

  implicit val UserProfileReads = (
    (__ \ "isPublic").read[Boolean] and
    (__ \ "firstName").read[String] and
    (__ \ "lastName").read[String] and
    (__ \ "email").read[String] and
    (__ \ "description").readOpt[String] and
    (__ \ "funFact").readOpt[String] and
    (__ \ "websites").read[List[String]] and
    (__ \ "twitter").readOpt[String] and
    (__ \ "linkedIn").readOpt[String]
  )(UserProfile.apply _)

  implicit val UserProfileWrites = (
    (__ \ "isPublic").write[Boolean] and
    (__ \ "firstName").write[String] and
    (__ \ "lastName").write[String] and
    (__ \ "email").write[String] and
    (__ \ "description").writeOpt[String] and
    (__ \ "funFact").writeOpt[String] and
    (__ \ "websites").write[List[String]] and
    (__ \ "twitter").writeOpt[String] and
    (__ \ "linkedIn").writeOpt[String]
  )(unlift(UserProfile.unapply))

  implicit val OrganizationEntryReads = (
    (__ \ "uri").read[String] and
    (__ \ "name").read[String] and
    (__ \ "countryCode").read[String]
  )(OrganizationEntry.apply _)

  implicit val OrganizationEntryWrites = (
    (__ \ "uri").write[String] and
    (__ \ "name").write[String] and
    (__ \ "countryCode").write[String]
  )(unlift(OrganizationEntry.unapply))

  implicit val NodeReads = (
    (__ \ "nodeId").read[String] and
    (__ \ "name").read[String] and
    (__ \ "orgId").read[String]
  )({ (nId: String, nName: String, nOrgId: String) => new Node {
    def nodeId: String = nId
    def name: String = nName
    def orgId: String = nOrgId
    def isLocal: Boolean = false
  }})

  implicit val NodeWrites = (
    (__ \ "nodeId").write[String] and
    (__ \ "name").write[String] and
    (__ \ "orgId").write[String]
  )({ node: Node =>
    (node.nodeId, node.name, node.orgId)
  })



  val log = Logger("CultureHub")

  val host = if (commonsHost.endsWith("/")) commonsHost.substring(0, commonsHost.length() - 1) else commonsHost

  val apiQueryParams = Seq(
    ("apiToken" -> apiToken),
    ("apiOrgId" -> orgId),
    ("apiNode" -> node)
  )

  private def get[T](path: String, queryParams: (String, String)*): Option[Response] = call(path, None, "GET", queryParams)

  private def post[T](path: String, queryParams: (String, String)*): Option[Response] = call(path, None, "POST", queryParams)

  private def delete[T](path: String, queryParams: (String, String)*): Option[Response] = call(path, None, "DELETE", queryParams)

  private def postWithBody[T <: JsValue](path: String, body: T, queryParams: (String, String)*): Option[Response] = call(path, Some(body), "POST", queryParams)

  private def call[T <: JsValue](path: String, body: Option[T], method: String = "GET", queryParams: Seq[(String, String)], retry: Int = 0): Option[Response] = {
    val wsCall = WS.url(host + path).withQueryString(queryParams ++ apiQueryParams: _ *)
    val callInvocation = method match {
      case "GET" => wsCall.get()
      case "POST" if (body.isDefined) => wsCall.post(body.get)
      case "POST" if (body.isEmpty) => wsCall.post("")
      case "DELETE" => wsCall.delete()
      case _ => throw new RuntimeException("Should not be here")
    }
    try {
      Some(Await.result(callInvocation, 5 seconds))
    } catch {
      case timeout: TimeoutException =>
        // retry
        if(retry < 3) {
          call(path, body, method, queryParams, retry + 1)
        } else {
          Logger("CultureHub").error("Still getting a timeout error while contacting CultureCommons, after %s attempts".format(retry), timeout)
          None
        }
      case t: Throwable =>
        Logger("CultureHub").error("Error contacting CultureCommons", t)
        None
    }
  }

  def connect(userName: String, password: String): Boolean = {
    val hashedPassword = MissingLibs.passwordHash(password, MissingLibs.HashType.SHA512)
    val hash = Crypto.sign(hashedPassword, userName.getBytes("utf-8"))
    get("/user/authenticate/" + URLEncoder.encode(hash, "utf-8")).map {
      response => response.status == OK
    }.getOrElse(false)
  }

  // registration

  def isUserNameTaken(userName: String): Boolean = {
    get("/user/" + userName).map {
      response => response.status == OK
    }.getOrElse(true)
  }

  def isEmailTaken(email: String): Boolean = {
    get("/user/query", ("field" -> "email"), ("value" -> email)).map {
      response => response.status == OK
    }.getOrElse(true)
  }

  def isAccountActive(email: String): Boolean = {
    get("/user/active/" + email).map {
      response => response.status == OK
    }.getOrElse(false)
  }

  def registerUser(userName: String, node: String, firstName: String, lastName: String, email: String, password: String): Option[String] = {
    post("/user/register", ("userName" -> userName), ("node" -> node), ("firstName" -> firstName),
      ("lastName" -> lastName),
      ("email" -> email),
      ("password" -> MissingLibs.passwordHash(password, MissingLibs.HashType.SHA512))).map {

      response =>
        if (response.status == OK) {
          Some(response.body)
        } else {
          None
        }
    }.getOrElse(None)
  }

  def activateUser(activationToken: String): Option[RegisteredUser] = {
    post("/user/activate/" + activationToken).map {
      response =>
        if (response.status == OK) {
          val json = Json.parse(response.body)
          Some(RegisteredUser(
            (json \ "userName").as[String],
            (json \ "firstName").as[String],
            (json \ "lastName").as[String],
            (json \ "email").as[String]
          ))
        } else {
          // TODO logging
          None
        }
    }.getOrElse(None)
  }

  def preparePasswordReset(email: String): Option[String] = {
    post("/user/preparePasswordReset/" + email).map {
      response =>
        if (response.status == OK) {
          Some(response.body)
        } else {
          None
        }
    }.getOrElse(None)
  }

  def resetPassword(resetToken: String, newPassword: String): Boolean = {
    post("/user/changePassword/" + resetToken, ("newPassword" -> MissingLibs.passwordHash(newPassword, MissingLibs.HashType.SHA512))).map {
      response => response.status == OK
    }.getOrElse(false)
  }

  // user profile

  def getUserProfile(userName: String): Option[UserProfile] = {
    get("/user/profile/" + userName).map {
      response =>
        if (response.status == OK) {
          Json.fromJson[UserProfile](response.json).asOpt
        } else {
          None
        }
    }.getOrElse(None)
  }

  def updateUserProfile(userName: String, profile: UserProfile): Boolean = {
    postWithBody("/user/profile/" + userName, Json.toJson[UserProfile](profile)).map {
      response => response.status == OK
    }.getOrElse(false)
  }

  // organization

  def exists(orgId: String): Boolean = {
    get("/organization/" + orgId).map { response => response.status == OK }.getOrElse(true)
  }

  def queryByOrgId(query: String): Seq[OrganizationProfile] = {
    get("/organization/query", "field" -> "name", "value" -> query).map { response =>
      if (response.status == OK) {
        Json.fromJson[Seq[OrganizationProfile]](response.json).asOpt.getOrElse(Seq.empty)
      } else {
        Seq.empty
      }
    }.getOrElse(Seq.empty)
  }

  def isAdmin(orgId: String, userName: String): Boolean = {
    get("/organization/" + orgId + "/admin/" + userName).map {
      response => response.status == OK
    }.getOrElse(false)
  }

  def listAdmins(orgId: String): List[String] = {
    get("/organization/" + orgId + "/admin/list").map {
      response => if (response.status == OK) {
        Json.parse(response.body).as[List[String]]
      } else {
        List()
      }
    }.getOrElse(List())
  }

  def addAdmin(orgId: String, userName: String): Boolean = {
    post("/organization/" + orgId + "/admin/" + userName).map {
      response => response.status == OK
    }.getOrElse(false)
  }

  def removeAdmin(orgId: String, userName: String): Boolean = {
    delete("/organization/" + orgId + "/admin/" + userName).map {
      response => response.status == OK
    }.getOrElse(false)
  }

  def getName(orgId: String, language: String): Option[String] = {
    get("/organization/" + orgId).map {
      response =>
        if (response.status == OK) {
          Json.fromJson[OrganizationProfile](response.json).asOpt.map(_.name.get(language).getOrElse(""))
        } else {
          None
        }
    }.getOrElse(None)
  }

  // nodes

  def registerNode(node: Node, userName: String) {
    post(
      "/node/register",
      "orgId" -> node.orgId,
      "nodeId" -> node.nodeId,
      "name" -> node.name,
      "apiUserName" -> userName
    ).foreach { response =>
      response.status match {
        case BAD_REQUEST =>
          log.error("Error registering node %s : ".format(node) + response.body)
          throw new RuntimeException("Could not register node: " + response.body)
        case OK => // we're ok
        case other@_ =>
          log.error("Could not register node %s: %s".format(node, response.body))
          throw new RuntimeException("Error registering node: " + response.body)
      }
    }
  }

  def updateNode(node: Node) {
    post(
      "/node/%s/%s/update".format(node.orgId, node.nodeId),
      "name" -> node.name
    ).foreach { response =>
      response.status match {
        case UNAUTHORIZED =>
          log.warn("Couldn't update node %s because access is unauthorized: ".format(node) + response.body)
          throw new RuntimeException("Not allowed to update node: " + response.body)
        case NOT_FOUND =>
          log.warn("Node %s could not be found".format(node))
          throw new RuntimeException("Node to update could not be found")
        case OK => // ok
        case other@_ =>
          log.error("Could not update node %s: %s".format(node, response.body))
          throw new RuntimeException("Error updating node: " + response.body)
      }
    }
  }

  def removeNode(node: Node) {
    delete("/node/%s/%s".format(node.orgId, node.nodeId)).foreach { response =>
      response.status match {
        case UNAUTHORIZED =>
          log.warn("Couldn't remove node %s because access is unauthorized: ".format(node) + response.body)
          throw new RuntimeException("Not allowed to remove node: " + response.body)
        case NOT_FOUND =>
          log.warn("Node %s could not be found".format(node))
          throw new RuntimeException("Node to remove could not be found")
        case OK => // ok
        case other@_ =>
          log.error("Could not remove node %s: %s".format(node, response.body))
          throw new RuntimeException("Error removing node: " + response.body)
      }
    }
  }

  def listMembers(node: Node): Seq[String] = {
    get(
      "/node/%s/%s/user/list".format(node.orgId, node.nodeId)
    ).map { response =>
      response.status match {
        case OK =>
          (response.json \ "members").asOpt[Seq[String]].getOrElse(Seq.empty)
        case _ =>
          Seq.empty
      }
    }.getOrElse(Seq.empty)
  }

  def addMember(node: Node, userName: String) {
    post(
      "/node/%s/%s/user/add".format(node.orgId, node.nodeId),
      "userName" -> userName
    ).foreach { response =>
        response.status match {
          case UNAUTHORIZED =>
            log.warn("Couldn't add member to node %s because access is unauthorized: ".format(node) + response.body)
            throw new RuntimeException("Not allowed to add member to node: " + response.body)
          case NOT_FOUND =>
            log.warn("Node %s could not be found".format(node))
            throw new RuntimeException("Node to update could not be found")
          case OK => // ok
          case other@_ =>
            log.error("Could not add member to node %s: %s".format(node, response.body))
            throw new RuntimeException("Error adding member to node: " + response.body)
        }
      }
    }

  def removeMember(node: Node, userName: String) {
    delete(
      "/node/%s/%s/user/%s".format(node.orgId, node.nodeId, userName)
    ).foreach { response =>
        response.status match {
          case UNAUTHORIZED =>
            log.warn("Couldn't remove member to node %s because access is unauthorized: ".format(node) + response.body)
            throw new RuntimeException("Not allowed to remove member from node: " + response.body)
          case NOT_FOUND =>
            log.warn("Node %s could not be found".format(node))
            throw new RuntimeException("Node to update could not be found")
          case OK => // ok
          case other@_ =>
            log.error("Could not remove member from node %s: %s".format(node, response.body))
            throw new RuntimeException("Error removing member from node: " + response.body)
        }
      }
  }

  // node directory

  def findOneById(nodeId: String): Option[Node] = {
    get("/node/" + nodeId).flatMap { response =>
      if (response.status == OK) {
        Json.fromJson[Node](response.json).asOpt
      } else {
        None
      }
    }
  }

  def listEntries: Seq[Node] = {
    get("/node/list").flatMap { response =>
      if (response.status == OK) {
        Json.fromJson[List[Node]](response.json).asOpt
      } else {
        None
      }
    }.getOrElse(Seq.empty)
  }

  // directory

  def findOrganization(query: String): List[OrganizationEntry] = {
    get("/directory/organization/query", "query" -> URLEncoder.encode(query, "utf-8")).map {
      response =>
        if(response.status == OK) {
          Json.fromJson[List[OrganizationEntry]](response.json).asOpt.getOrElse(List.empty)
        } else {
          List.empty
        }
    }.getOrElse(List.empty)
  }

  def findOrganizationByName(name: String): Option[OrganizationEntry] = {
    get("/directory/organization/byName", "name" -> URLEncoder.encode(name, "utf-8")).map {
      response =>
        if(response.status == OK) {
          Json.fromJson[OrganizationEntry](response.json).asOpt
        } else {
          None
        }
    }.getOrElse(None)
  }

}

case class OrganizationProfile(orgId: String, name: Map[String, String])

