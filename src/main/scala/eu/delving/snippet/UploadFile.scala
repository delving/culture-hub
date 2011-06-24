package eu.delving.snippet

import net.liftweb._
import http._
import util._
import Helpers._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class UploadFile {
  def render = "type=file [name]" #>
          SHtml.fileUpload(fph => println("Got a file " + fph.fileName)).attribute("name").get
}