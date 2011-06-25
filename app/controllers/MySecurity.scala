package controllers

class MySecurity extends Security {

  def authenticate(username: String, password: String): Boolean = {
    true
  }


}