package bootstrap.liftweb

import _root_.net.liftweb.common._
import _root_.net.liftweb.http._
import _root_.net.liftweb.sitemap._
import _root_.eu.delving.model._
import javax.mail.{Authenticator, PasswordAuthentication}
import net.liftweb.mongodb.{MongoDB, MongoHost, DefaultMongoIdentifier, MongoAddress}
import eu.delving.lib.MetaRepoService
import net.liftweb.util._

/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot extends Loggable {
  def boot() {

    MongoDB.defineDb(
      DefaultMongoIdentifier,
      MongoAddress(MongoHost(), "lift_services")
    )

    LiftRules addToPackages "eu.delving"

    val pages = List(
      Menu("Home") / "index",
      Menu("Static") / "static" / **,
      Menu("User") / "user",
      Menu("Profile") / "user" / "profile",
      Menu("Label") / "user" / "label",
      Menu("Collection") / "user" / "collection"
    )

    LiftRules.setSiteMap(SiteMap((pages ::: MetaRepoService.sitemap ::: User.sitemap): _*))

    LiftRules.statelessRewrite append {
      case RewriteRequest(ParsePath(Allowed(userName) :: Nil, "", true, false), GetRequest, http) =>
        RewriteResponse("user" :: Nil, Map("userName" -> userName))
      case RewriteRequest(ParsePath(Allowed(userName) :: "profile" :: Nil, "", true, false), GetRequest, http) =>
        RewriteResponse("user" :: "profile" :: Nil, Map("userName" -> userName))
      case RewriteRequest(ParsePath(Allowed(userName) :: "label" :: Nil, "", true, false), GetRequest, http) =>
        RewriteResponse("user" :: "label" :: Nil, Map("userName" -> userName))
      case RewriteRequest(ParsePath(Allowed(userName) :: "label" :: Allowed(labelName) :: Nil, "", true, false), GetRequest, http) =>
        RewriteResponse("user" :: "label" :: Nil, Map("userName" -> userName, "labelName" -> labelName))
      case RewriteRequest(ParsePath(Allowed(userName) :: "collection" :: Nil, "", true, false), GetRequest, http) =>
        RewriteResponse("user" :: "collection" :: Nil, Map("userName" -> userName))
      case RewriteRequest(ParsePath(Allowed(userName) :: "collection" :: Allowed(collectionName) :: Nil, "", true, false), GetRequest, http) =>
        RewriteResponse("user" :: "collection" :: Nil, Map("userName" -> userName, "collectionName" -> collectionName))
    }

    // spinny image
    LiftRules.ajaxStart = Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)
    LiftRules.ajaxEnd = Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

    LiftRules.early.append(_.setCharacterEncoding("UTF-8"))
    LiftRules.loggedInTest = Full(() => User.loggedIn_?)

    LiftRules.dispatch append MetaRepoService

    var isAuth = Props.get("mail.smtp.auth", "false").toBoolean
    Mailer.customProperties = Props.get("mail.smtp.host", "localhost") match {
      case "smtp.gmail.com" =>
        isAuth = true
        Map(
          "mail.smtp.host" -> "smtp.gmail.com",
          "mail.smtp.port" -> "587",
          "mail.smtp.auth" -> "true",
          "mail.smtp.starttls.enable" -> "true"
        )
      case h => Map(
        "mail.smtp.host" -> h,
        "mail.smtp.port" -> Props.get("mail.smtp.port", "25"),
        "mail.smtp.auth" -> isAuth.toString
      )
    }

    if (isAuth) {
      (Props.get("mail.smtp.user"), Props.get("mail.smtp.pass")) match {
        case (Full(username), Full(password)) =>
          Mailer.authenticator = Full(new Authenticator() {
            override def getPasswordAuthentication = new
                            PasswordAuthentication(username, password)
          })
        case _ => logger.error("Username/password not supplied for Mailer.")
      }
    }
  }

  object Allowed {
    // todo: expand this to look for the FORBIDDEN inside of the string, and other variations
    def unapply(string: String): Option[String] = if (FORBIDDEN.contains(string)) None else Some(string)
  }

  val FORBIDDEN = Set(
    "object", "profile", "map", "graph", "label", "collection",
    "story", "user", "services", "portal", "api", "index",
    "add", "edit", "save", "delete", "update", "create", "search"
  )
}
