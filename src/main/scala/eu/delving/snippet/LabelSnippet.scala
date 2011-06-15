package eu.delving.snippet

import _root_.net.liftweb.util._
import Helpers._
import net.liftweb.http.S

class LabelSnippet {
  def userName = "#userName *" #> S.param("userName")
  def labelName = "#labelName *" #> S.param("labelName")

}
