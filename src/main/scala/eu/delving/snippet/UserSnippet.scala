package eu.delving.snippet

import _root_.net.liftweb.util._
import Helpers._
import net.liftweb.http.S

class UserSnippet {
  // bind the user's name into the element with id "userName"
  def userName = "#userName *" #> S.param("userName")

}
