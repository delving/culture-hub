package notifiers

import extensions.Email
import play.api.Play.current
import collection.mutable.ArrayBuffer
import models.{PortalTheme, User}
import java.io.{FileInputStream, File}
import collection.JavaConverters._
import core.ThemeInfo
import play.api.i18n.Messages


/**
 * Email notifications
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Mails {

  def activation(email: String, fullName: String, token: String, theme: PortalTheme) {
    Email(theme.emailTarget.systemFrom, "Welcome to CultureHub").to(email).withTemplate("Mails/activation.txt", 'fullName -> fullName, 'activationToken -> token, 'themeInfo -> new ThemeInfo(theme)).send()
  }

  def accountBlocked(user: User, contactEmail: String, theme: PortalTheme) {
    Email(theme.emailTarget.systemFrom, Messages("mail.subject.accountblocked")).to(user.email).withTemplate("Mails/accountBlocked.txt", 'userName -> user.userName, 'contactEmail -> contactEmail).send()
  }

  def newUser(subject: String, hub: String, userName: String, fullName: String, email: String, theme: PortalTheme) {
    Email(theme.emailTarget.systemFrom, subject).to(theme.emailTarget.exceptionTo).withTemplate("Mails/newUser.txt", 'fullName -> fullName, 'hub -> hub, 'userName -> userName, 'quote -> randomQuote()).send()
  }
  
  def resetPassword(user: User, resetPasswordToken: String, theme: PortalTheme) {
    Email(theme.emailTarget.systemFrom, "Reset your password").to(user.email).withTemplate("Mails/resetPassword.txt", 'user -> user, 'resetPasswordToken -> resetPasswordToken, 'themeInfo -> new ThemeInfo(theme)).send()
  }
  
  def reportError(subject: String, report: String, theme: PortalTheme) {
    Email(theme.emailTarget.systemFrom, subject).to(theme.emailTarget.exceptionTo).withTemplate("Mails/reportError.txt", 'report -> report, 'quote -> randomQuote(), 'themeInfo -> new ThemeInfo(theme)).send()
  }

  // ~~~~ some fun

  lazy val quotes: List[String] = {
    // quotes.txt courtesy of Rudy Velthuis - http://blogs.teamb.com/rudyvelthuis/2006/07/29/26308
    val f = new File(current.path, "/conf/quotes.txt")
    val lines = org.apache.commons.io.IOUtils.readLines(new FileInputStream(f), "utf-8").asScala
    val quotes = new ArrayBuffer[String]()
    val sb = new StringBuilder()
    try {
      for (line <- lines) {
        if (line == ".") {
          quotes += sb.result()
          sb.clear()
        } else {
          sb.append(line).append("\n")
        }
      }
    } catch {
      case t => t.printStackTrace()
    }
    quotes.toList
  }

  def randomQuote() = {
    val index = java.lang.Math.random() * quotes.size + 1
    quotes(index.toInt)
  }


}