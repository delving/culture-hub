package controllers

import play._
import play.mvc._

class MySecurity extends Security {

  def authenticate(username: String, password: String):Boolean = {
    true
  }


}

object Application extends Controller with Secure {
    
    import views.Application._
    
    def index = {
        html.index(title = "Howdy!", username = connectedUser)
    }
    
}