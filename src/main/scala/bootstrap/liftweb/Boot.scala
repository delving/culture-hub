package bootstrap.liftweb

import _root_.net.liftweb.common._
import _root_.net.liftweb.http._
import _root_.net.liftweb.sitemap._
import _root_.eu.delving.model._
import javax.mail.{Authenticator, PasswordAuthentication}
import net.liftweb.mongodb.{MongoDB, MongoHost, DefaultMongoIdentifier, MongoAddress}
import net.liftweb.util._
import eu.delving.lib.{OaiPmhService, BrowseService, MetaRepoService}

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
      Menu("User") / "service" / "user",
      Menu("Profile") / "service" / "profile",
      Menu("Label") / "service" / "label",
      Menu("Labels") / "service" / "labels",
      Menu("Collection") / "service" / "collection",
      Menu("Collections") / "service" / "collections",
      Menu("Image") / "service" / "image"
    )

    LiftRules.setSiteMap(SiteMap((pages ::: MetaRepoService.sitemap ::: User.sitemap): _*))

    LiftRules.statelessRewrite append {

      case RewriteRequest(ParsePath(Allowed(userName) :: Nil, "", true, false), GetRequest, http) =>
        RewriteResponse("service" :: "user" :: Nil, Map("userName" -> userName))

      case RewriteRequest(ParsePath(Allowed(userName) :: "profile" :: Nil, "", true, false), GetRequest, http) =>
        RewriteResponse("service" :: "profile" :: Nil, Map("userName" -> userName))

      case RewriteRequest(ParsePath(Allowed(userName) :: "label" :: Nil, "", true, false), GetRequest, http) =>
        RewriteResponse("service" :: "labels" :: Nil, Map("userName" -> userName))

      case RewriteRequest(ParsePath(Allowed(userName) :: "label" :: Allowed(labelName) :: Nil, "", true, false), GetRequest, http) =>
        RewriteResponse("service" :: "label" :: Nil, Map("userName" -> userName, "labelName" -> labelName))

      case RewriteRequest(ParsePath(Allowed(userName) :: "collection" :: Nil, "", true, false), GetRequest, http) =>
        RewriteResponse("service" :: "collections" :: Nil, Map("userName" -> userName))

      case RewriteRequest(ParsePath(Allowed(userName) :: "collection" :: Allowed(collectionName) :: Nil, "", true, false), GetRequest, http) =>
        RewriteResponse("service" :: "collection" :: Nil, Map("userName" -> userName, "collectionName" -> collectionName))

    }

    LiftRules.templateSuffixes

    // spinny image
    LiftRules.ajaxStart = Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)
    LiftRules.ajaxEnd = Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

    LiftRules.early.append(_.setCharacterEncoding("UTF-8"))
    LiftRules.loggedInTest = Full(() => User.loggedIn_?)

    LiftRules.dispatch append MetaRepoService
    LiftRules.dispatch append OaiPmhService
    LiftRules.dispatch append BrowseService

    // do not apply lift foo for requests meant for the fcgi-bin servlet
    LiftRules.liftRequest.append({
      case r if (r.path.partPath match {
        case "fcgi-bin" :: _ => true case _ => false
      }) => false
    })

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
    def unapply(string: String): Option[String] = {
      FORBIDDEN map {
        f => if(string.contains(f)) {
          return None
        }
      }
      Some(string)
    }
  }

  val FORBIDDEN = Set(
    "object", "profile", "map", "graph", "label", "collection", "image", "fcgi-bin",
    "story", "user", "service", "services", "portal", "api", "index",
    "add", "edit", "save", "delete", "update", "create", "search"
  )

  ResourceServer.allow {
    case "css" :: _ => true
  }

}
