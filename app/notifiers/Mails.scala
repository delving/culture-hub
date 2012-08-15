package notifiers

import extensions.Email
import models.DomainConfiguration
import core.ThemeInfo
import play.api.i18n.Lang
import util.Quotes


/**
 * Email notifications
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Mails {

  def activation(email: String, fullName: String, token: String, host: String)(implicit configuration: DomainConfiguration, lang: Lang) {
    Email(configuration.emailTarget.systemFrom, "Welcome to CultureHub").to(email).withTemplate("Mails/activation.txt", lang.language, 'fullName -> fullName, 'activationToken -> token, 'themeInfo -> new ThemeInfo(configuration), 'host -> host).send()
  }

  def newUser(subject: String, hub: String, userName: String, fullName: String, email: String)(implicit configuration: DomainConfiguration, lang: Lang) {
    Email(configuration.emailTarget.systemFrom, subject).to(configuration.emailTarget.exceptionTo).withTemplate("Mails/newUser.txt", lang.language, 'fullName -> fullName, 'hub -> hub, 'userName -> userName, 'email -> email, 'quote -> Quotes.randomQuote()).send()
  }
  
  def resetPassword(email: String, resetPasswordToken: String, host: String)(implicit configuration: DomainConfiguration, lang: Lang) {
    Email(configuration.emailTarget.systemFrom, "Reset your password").to(email).withTemplate("Mails/resetPassword.txt", lang.language, 'resetPasswordToken -> resetPasswordToken, 'themeInfo -> new ThemeInfo(configuration), 'host -> host).send()
  }

}