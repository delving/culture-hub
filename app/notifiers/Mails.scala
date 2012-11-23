package notifiers

import extensions.Email
import models.DomainConfiguration
import core.ThemeInfo
import play.api.i18n.{Messages, Lang}

/**
 * Email notifications
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Mails {

  def activation(email: String, fullName: String, token: String, host: String)(implicit configuration: DomainConfiguration, lang: Lang) {
    val themeInfo = new ThemeInfo(configuration)
    Email(configuration.emailTarget.systemFrom, "Welcome to CultureHub").
      to(email).
      withContent(
      """
        |Hi %s !
        |
        |%s:
        |
        |http://%s/registration/activate/%s
      """.stripMargin.format(
            fullName,
            Messages("mail.message.activateaccount", themeInfo.siteName),
            host,
            token
          )
    ).send()
  }

  def newUser(subject: String, hub: String, userName: String, fullName: String, email: String)(implicit configuration: DomainConfiguration, lang: Lang) {
    Email(configuration.emailTarget.systemFrom, subject).
      to(configuration.emailTarget.exceptionTo).
      withContent(
      """
        |Master,
        |
        |a new awesome user registered on the CultureHub %s:
        |
        |User:   %s
        |Name:   %s
        |Email:  %s
      """.stripMargin.format(
            hub,
            userName,
            fullName,
            email
          )
    ).send()
  }
  
  def resetPassword(email: String, resetPasswordToken: String, host: String)(implicit configuration: DomainConfiguration, lang: Lang) {
    val themeInfo = new ThemeInfo(configuration)
    Email(configuration.emailTarget.systemFrom, "Reset your password").
      to(email).
      withContent(
      """
        |%s!
        |
        |%s:
        |
        |http://%s/registration/resetPassword/%s
      """.stripMargin.format(
        Messages("ui.label.hi"),
        Messages("mail.message.resetpassword", themeInfo.siteName),
        host,
        resetPasswordToken
      )).
      send()
  }

}