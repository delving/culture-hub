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
    val images = Props.get("image.store.path").openTheBox
    if(images == null) {
      throw new RuntimeException("You need configure an image storage path in default.props")
    }
    <head>{Script(OnLoad(Call("displayImage", images +  "/smallballs.tif")))}</head>
  }
}