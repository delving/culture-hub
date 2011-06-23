package eu.delving.snippet

import _root_.net.liftweb.util._
import xml.NodeSeq
import net.liftweb.http.js.JE.Call
import net.liftweb.http._
import js._
import JE._
import JsCmds._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class ImageSnippet {

  def loadImage(xml: NodeSeq): NodeSeq = {
    // until we have a configuration mechanism figure out the absolute path via a JVM arg
    val arg = System.getProperty("culture-hub.root.path")
    if(arg == null) {
      throw new RuntimeException("You need to start the webapp with a -Dculture-hub.root.path JVM arg that points to the absolute path of culture-hub")
    }
    <head>{Script(OnLoad(Call("displayImage", arg +  "/src/main/webapp/images/smallballs.tif")))}</head>
  }
}