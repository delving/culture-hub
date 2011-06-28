package eu.delving.snippet

import _root_.net.liftweb.util._
import xml.NodeSeq
import net.liftweb.http.js.JE.Call
import net.liftweb.http._
import js._
import JE._
import JsCmds._
import eu.delving.lib.Util

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class ImageSnippet extends Util {

  def loadImage(xml: NodeSeq): NodeSeq = {
    val images = getPath("image.store.path")
    <head>{Script(OnLoad(Call("displayImage", images +  "/" + S.param("imageName").openOr("smallballs") + ".tif")))}</head>
  }
}