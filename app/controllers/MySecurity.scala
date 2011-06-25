package controllers

import controllers.Security

class MySecurity extends Security {

  def authenticate(username: String, password: String): Boolean = {
    true
  }


}