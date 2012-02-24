package extensions

import org.apache.commons.mail.SimpleEmail
import play.api.Logger
import play.api.Play.current
import eu.delving.templates.GroovyTemplatesPlugin

/**
 * Email sending
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

private[extensions] case class MailBuilder(subject: String, content: String = "", from: String, to: Seq[String] = Seq.empty, bcc: Seq[String] = Seq.empty) {

  val hostName = current.configuration.getString("mail.smtp.host").getOrElse("")
  val smtpPort = current.configuration.getInt("mail.smtp.port").getOrElse(587)
  val mailerType = current.configuration.getString("mail.smtp.type").getOrElse("mock")


  def to(to: String*): MailBuilder = this.copy(to = this.to ++ to)
  def bcc(bcc: String*): MailBuilder = this.copy(bcc = this.bcc ++ bcc)

  def withContent(content: String) = this.copy(content = content)
  def withTemplate(name: String, args: (Symbol, AnyRef)*) = this.copy(content = renderMailTemplate(name, args.map(e => (e._1.name, e._2)).toMap))

  def send() {

    if(mailerType == "mock") {

      val mail = """

      ~~~~ Mock mailer ~~~~~
      Mail from: %s
      Mail to:   %s
      BCC:       %s

      Subject:   %s

      %s
      ~~~~~~~~~~~~~~~~~~~~~~

      """.format(from, to.mkString(", "), bcc.mkString(", "), subject, content)

      Logger("CultureHub").info(mail)

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
    current.plugin[GroovyTemplatesPlugin].map(_.renderTemplate(name, args)).getOrElse(Right("")).fold(
      left => throw(left),
      right => right
    )
  }

}

object Email {

  def apply(from: String, subject: String): MailBuilder = new MailBuilder(from = from, subject = subject)
}
