package bootstrap.liftweb

import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import _root_.net.liftweb.http._
import _root_.net.liftweb.sitemap._
import Helpers._
import _root_.eu.delving.model._
import javax.mail.{Authenticator, PasswordAuthentication}
import net.liftweb.mongodb.{MongoDB, MongoHost, DefaultMongoIdentifier, MongoAddress}
import eu.delving.lib.MetaRepoService

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

    val pages = List(Menu("Home") / "index")

    LiftRules.setSiteMap(SiteMap((pages ::: MetaRepoService.sitemap ::: User.sitemap): _*))

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
}
