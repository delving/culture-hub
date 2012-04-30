package notifiers

import extensions.Email
import models.PortalTheme
import core.ThemeInfo
import play.api.i18n.Lang
import util.Quotes


/**
 * Email notifications
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Mails {

  def activation(email: String, fullName: String, token: String, theme: PortalTheme)(implicit lang: Lang) {
    Email(theme.emailTarget.systemFrom, "Welcome to CultureHub").to(email).withTemplate("Mails/activation.txt", lang.language, 'fullName -> fullName, 'activationToken -> token, 'themeInfo -> new ThemeInfo(theme)).send()
  }

//  def accountBlocked(user: User, contactEmail: String, theme: PortalTheme) {
//    Email(theme.emailTarget.systemFrom, Messages("mail.subject.accountblocked")).to(user.email).withTemplate("Mails/accountBlocked.txt", 'userName -> user.userName, 'contactEmail -> contactEmail).send()
//  }

  def newUser(subject: String, hub: String, userName: String, fullName: String, email: String, theme: PortalTheme)(implicit lang: Lang) {
    Email(theme.emailTarget.systemFrom, subject).to(theme.emailTarget.exceptionTo).withTemplate("Mails/newUser.txt", lang.language, 'fullName -> fullName, 'hub -> hub, 'userName -> userName, 'quote -> Quotes.randomQuote()).send()
  }
  
  def resetPassword(email: String, resetPasswordToken: String, theme: PortalTheme)(implicit lang: Lang) {
    Email(theme.emailTarget.systemFrom, "Reset your password").to(email).withTemplate("Mails/resetPassword.txt", lang.language, 'resetPasswordToken -> resetPasswordToken, 'themeInfo -> new ThemeInfo(theme)).send()
  }

}