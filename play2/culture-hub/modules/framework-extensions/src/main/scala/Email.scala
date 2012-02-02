package extensions

import org.apache.commons.mail.SimpleEmail
import play.api.Logger
import play.api.Play.current
import play.templates.GroovyTemplatesPlugin

/**
 * Email sending
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Email(subject: String, content: String = "", from: String, to: Seq[String] = Seq.empty, bcc: Seq[String] = Seq.empty) {

  val hostName = current.configuration.getString("mail.smtp.host").getOrElse("")
  val smtpPort = current.configuration.getInt("mail.smtp.port").getOrElse(587)
  val mockMail = current.configuration.getString("mail.smtp").getOrElse("default") == "mock"


  def to(to: String*): Email = this.copy(to = this.to ++ to)
  def bcc(bcc: String*): Email = this.copy(bcc = this.bcc ++ bcc)

  def withContent(content: String) = this.copy(content = content)
  def withTemplate(name: String, args: (Symbol, AnyRef)*) = this.copy(content = renderMailTemplate(name, args.map(e => (e._1.toString(), e._2)).toMap))

  def send() {

    if(mockMail) {

      val mail = """

      ~~~~ Mock mailer ~~~~~
      Mail from: %s
      Mail to:   %s
      BCC:       %s

      Subject:   %s

      %s

      ~~~~~~~~~~~~~~~~~~~~~~

      """.format(from, to.mkString(", "), bcc.mkString(", "), subject, content)

      Logger("MockMailer").info(mail)

    } else {

      val email = new SimpleEmail
      email.setHostName(hostName)
      email.setSmtpPort(smtpPort)
      email.setFrom(from)
      to foreach(email.addTo(_))
      bcc foreach(email.addBcc(_))
      email.setSubject(subject)
      email.setMsg(content)

      email.send()
    }


  }

  private def renderMailTemplate(name: String, args: Map[String, AnyRef]) = {
    current.plugin[GroovyTemplatesPlugin].map(_.renderTemplate(name, args)).getOrElse("")
  }

}

object Email {

  def apply(from: String, subject: String): Email = Email(from = from, subject = subject)
}
