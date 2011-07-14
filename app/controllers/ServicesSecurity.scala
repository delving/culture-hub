package controllers

import models.User

class ServicesSecurity extends Security {

  def authenticate(username: String, password: String): Boolean = {
      User.connect(username, password)
  }


}