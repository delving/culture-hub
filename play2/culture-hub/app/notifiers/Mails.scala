package notifiers

import extensions.Email
import play.api.Play
import play.api.Play.current
import collection.mutable.ArrayBuffer
import models.{PortalTheme, User}


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Mails {

  def activation(user: User, token: String, theme: PortalTheme) {
    Email(theme.emailTarget.systemFrom, "Welcome to CultureHub").
      to(user.email).
      withTemplate("/Mails/activation.txt", 'user -> user, 'activationToken -> token).
      send()
  }
  
  def reportError(subject: String, report: String, theme: PortalTheme) {
    Email(theme.emailTarget.systemFrom, subject).
      to(theme.emailTarget.exceptionTo).
      withTemplate("/Mails/reportError.txt", 'report -> report, 'quote -> randomQuote())
  }

  // ~~~~ some fun

  val quotes: List[String] = {
    // quotes.txt courtesy of Rudy Velthuis - http://blogs.teamb.com/rudyvelthuis/2006/07/29/26308
    val f = Play.getFile("/app/views/Mails/quotes.txt")
    val lines = scala.io.Source.fromFile(f).getLines()
    val quotes = new ArrayBuffer[String]()
    val sb = new StringBuilder()
    for (line <- lines) {
      if (line == ".") {
        quotes += sb.result()
        sb.clear()
      } else {
        sb.append(line).append("\n")
      }
    }
    quotes.toList
  }

  def randomQuote() = {
    val index = java.lang.Math.random() * quotes.size + 1
    quotes(index.toInt)
  }


}