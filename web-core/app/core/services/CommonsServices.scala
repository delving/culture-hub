package core.services

import core._
import play.api.libs.Crypto
import play.api.libs.ws.{Response, WS}
import play.api.libs.json._
import play.api.Logger
import java.util.concurrent.TimeoutException
import java.net.URLEncoder
import extensions.{JJson, MissingLibs}
import eu.delving.definitions.OrganizationEntry

/**
 * TODO harden this, error handling, logging... for now we always return the worst case scenario in case of an error. however we should make the clients
 * of these services aware of lookup problems
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CommonsServices(commonsHost: String, orgId: String, apiToken: String, node: String) extends AuthenticationService with RegistrationService with UserProfileService with OrganizationService with DirectoryService with play.api.http.Status {


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
    val wsCall = WS.url(host + path).withQueryString(queryParams.map(t => (t._1, URLEncoder.encode(t._2, "utf-8"))) ++ apiQueryParams: _ *)
    val callInvocation = method match {
      case "GET" => wsCall.get()
      case "POST" if (body.isDefined) => wsCall.post(body.get)
      case "POST" if (body.isEmpty) => wsCall.post("")
      case "DELETE" => wsCall.delete()
      case _ => throw new RuntimeException("Should not be here")
    }
    try {
      callInvocation.await.fold(t => None, r => Some(r))
    } catch {
      case timeout: TimeoutException =>
        // retry
        if(retry < 3) {
          call(path, body, method, queryParams, retry + 1)
        } else {
          Logger("CultureHub").error("Still getting a timeout error while contacting CultureCommons, after %s attempts".format(retry), timeout)
          None
        }
      case t =>
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
          import UserProfileFormat._
          Some(Json.fromJson[UserProfile](Json.parse(response.body)))
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
    get("/organization/" + orgId).map {
      response => response.status == OK
    }.getOrElse(true)
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
          import OrganizationProfileFormat._
          Json.fromJson[OrganizationProfile](Json.parse(response.body)).name.get(language)
        } else {
          None
        }
    }.getOrElse(None)
  }

  // directory

  def findOrganization(query: String): List[OrganizationEntry] = {
    get("/directory/organization/query", "query" -> URLEncoder.encode(query, "utf-8")).map {
      response =>
        if(response.status == OK) {
          import OrganizationEntryFormat._
          Json.fromJson[List[OrganizationEntry]](Json.parse(response.body))
        } else {
          List.empty
        }
    }.getOrElse(List.empty)
  }

  def findOrganizationByName(name: String): Option[OrganizationEntry] = {
    get("/directory/organization/byName", "name" -> URLEncoder.encode(name, "utf-8")).map {
      response =>
        if(response.status == OK) {
          Some(Json.fromJson[OrganizationEntry](Json.parse(response.body)))
        } else {
          None
        }
    }.getOrElse(None)
  }

  // json un/marshalling

  import play.api.libs.json._

  implicit object UserProfileFormat extends Format[UserProfile] {


    def reads(json: JsValue) = UserProfile(
      isPublic = (json \ "isPublic").as[Boolean],
      firstName = (json \ "firstName").as[String],
      lastName = (json \ "lastName").as[String],
      email = (json \ "email").as[String],
      description = (json \ "description").asOpt[String],
      funFact = (json \ "funFact").asOpt[String],
      websites = (json \ "websites").asOpt[List[String]].getOrElse(List()),
      twitter = (json \ "twitter").asOpt[String],
      linkedIn = (json \ "linkedIn").asOpt[String]
    )

    def writes(o: UserProfile) = JsObject(
      List(
        "isPublic" -> JsBoolean(o.isPublic),
        "firstName" -> JsString(o.firstName),
        "lastName" -> JsString(o.lastName),
        "email" -> JsString(o.email)
      ) ::: o.description.map(d => List("description" -> JsString(d))).getOrElse(List())
        ::: o.funFact.map(d => List("funFact" -> JsString(d))).getOrElse(List())
        ::: List("websites" -> JsArray(o.websites.map(e => JsString(e)).toList))
        ::: o.twitter.map(d => List("twitter" -> JsString(d))).getOrElse(List())
        ::: o.linkedIn.map(d => List("linkedIn" -> JsString(d))).getOrElse(List())
    )
  }

  implicit object OrganizationProfileFormat extends Format[OrganizationProfile] {

    def reads(json: JsValue) = OrganizationProfile(
      orgId = (json \ "orgId").as[String],
      name = (json \ "name").as[Map[String, String]]
    )

    def writes(o: OrganizationProfile) = JsObject(
      List(
        "orgId" -> JsString(o.orgId),
        "name" -> JsObject(
          o.name.map(n => (n._1 -> JsString(n._2))).toList
        )
      )
    )
  }

  implicit object OrganizationEntryFormat extends Format[OrganizationEntry] {

    def reads(json: JsValue): OrganizationEntry = OrganizationEntry(
      uri = (json \ "uri").as[String],
      name = (json \ "name").as[String],
      countryCode = (json \ "countryCode").as[String]
    )

    def writes(o: OrganizationEntry): JsValue = JsObject(
      Seq(
        "uri" -> JsString(o.uri),
        "name" -> JsString(o.name),
        "countryCode" -> JsString(o.countryCode)
      )
    )
  }




}

case class OrganizationProfile(orgId: String, name: Map[String, String])

