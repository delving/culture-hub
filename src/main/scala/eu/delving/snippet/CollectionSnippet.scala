package eu.delving.snippet

import _root_.net.liftweb.util._
import Helpers._
import net.liftweb.http.S

class CollectionSnippet {
  def userName = "#userName *" #> S.param("userName")
  def collectionName = "#collectionName *" #> S.param("collectionName")

}
