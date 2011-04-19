package eu.delving {
package snippet {

import _root_.scala.xml.{NodeSeq, Text}
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import _root_.java.util.Date
import eu.delving.lib._
import Helpers._
import model.User

class HelloWorld {
  lazy val date: Box[Date] = DependencyFactory.inject[Date] // inject the date

  // bind the date into the element with id "time"
  def howdy = "#time *" #> date.map(_.toString)

//  def present = "#present *" #> List(User.loggedIn_?.toString)
  def here = "#present *" #> User.loggedIn_?.toString

}

}
}
