package notifiers

import play.mvc.Mailer
import models.User
import controllers.{ThemeAware}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Mails extends Mailer with ThemeAware {

  def activation(user: User) = {
    setSubject("Welcome to CultureHub")
    addRecipient(user.email)
    setFrom(theme.emailTarget.systemFrom)
    send(user)
  }

}