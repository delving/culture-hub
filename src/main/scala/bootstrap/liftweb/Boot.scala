package bootstrap.liftweb

import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import _root_.net.liftweb.http._
import _root_.net.liftweb.http.provider._
import _root_.net.liftweb.sitemap._
import _root_.net.liftweb.sitemap.Loc._
import Helpers._
import _root_.eu.delving.model._
import javax.mail.{Authenticator, PasswordAuthentication}
import net.liftweb.mongodb.{MongoDB, MongoHost, DefaultMongoIdentifier, MongoAddress}
import java.io.{FileInputStream, File}
import rest.RestHelper

/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot extends Loggable {
  def boot {

    //    val localFile = () => {
    //      val file = new File("/lift-services.properties")
    //      if (file.exists) Full(new FileInputStream(file)) else Empty
    //    }
    //    Props.whereToLook = () => (("local", localFile) :: Nil)

    MongoDB.defineDb(
      DefaultMongoIdentifier,
      MongoAddress(MongoHost(), "lift_services")
    )

    LiftRules.addToPackages("eu.delving")

    LiftRules.setSiteMap(
      SiteMap((
        List(
          Menu(Loc("Home", List("index"), "Home")),
          Menu("Logout") / "logout",
          Menu(Loc("Static", Link(List("static"), true, "/static/index"), "Static Content")),
          Menu("Service") / "service",
          Menu("Safe Service") / "safe-service" >> TestAccess(() => if (User.notLoggedIn_?) Full(RedirectResponse("login")) else Empty )
//          Menu("Safe Service") / "safe-service" >> Test(request => User.loggedIn_?)
        ) :::
        User.sitemap
      ): _*)
    )

    // spinny image
    LiftRules.ajaxStart = Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)
    LiftRules.ajaxEnd = Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

    LiftRules.early.append(_.setCharacterEncoding("UTF-8"))
    LiftRules.loggedInTest = Full(() => User.loggedIn_?)

    LiftRules.dispatch append DelvingServices
    LiftRules.dispatch append {
      case Req("logout" :: Nil, _, GetRequest) =>
        S.request.foreach(_.request.session.terminate)
        S.redirectTo("/")
    }

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
