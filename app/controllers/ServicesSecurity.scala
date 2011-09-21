package controllers

import models.User
import play.mvc.Scope.Session

class ServicesSecurity extends Security {

  def authenticate(username: String,
                   password: String): Boolean = {
    User.connect(username, password, "cultureHub") // TODO
  }

  def onAuthenticated(username: String, session: Session) {
    val user = User.findByUsername(username, "cultureHub") // TODO
    if(user == None) {
      throw new RuntimeException("User %s was not found ".format(username))
    }
    session.put("connectedUserId", user.get._id.toString)
  }
}