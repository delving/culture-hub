package controllers

import models.User
import play.mvc.Scope.Session

class ServicesSecurity extends Security with Internationalization {

  def authenticate(username: String, password: String): Boolean = {
    User.connect(username, password)
  }

  def onAuthenticated(username: String, session: Session) {
    val user = User.findByUsername(username)
    if(user == None) {
      throw new RuntimeException(&("servicessecurity.userNotFound", username))
    }
    session.put("connectedUserId", user.get._id.toString)
    session.put(AccessControl.ORGANIZATIONS, user.get.organizations.mkString(","))
    session.put(AccessControl.GROUPS, user.get.groups.mkString(","))

  }
}