package controllers

import play.mvc.Controller
import play.Play
import java.io.File
import play.mvc.results.Result
import play.libs.IO

/**
 * Helper controller to pre-process assets such as jquery templates
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Asset extends Controller with Internationalization {

  def get(path: String): Result = {
    val f = new File(Play.applicationPath + path)
    if(!f.exists()) return NotFound("Asset at path %s not found".format(path))
    val content = IO.readContentAsString(f)
    val messages = "\\&\\{([^\\}]*)\\}".r.findAllIn(content).matchData.map(m => (m.group(0), m.group(1))).map {
      m =>
        val elems: Array[String] = m._2.split(",").map(e => e.trim.substring(1, e.trim.length() -1))
        val key: String = elems(0)
        val args: Array[String] = elems.slice(1, elems.length)
        (m._1, key, args)
    }
    Text(messages.foldLeft(content) { (r, c) => r.replace(c._1, &(c._2, c._3 : _*))})
  }

}