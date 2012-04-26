package util

import play.api.Play.current
import java.io.{FileInputStream, File}
import collection.mutable.ArrayBuffer
import collection.JavaConverters._

/**
 * Some wisdom
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Quotes {

  lazy val quotes: List[String] = {
    // quotes.txt courtesy of Rudy Velthuis - http://blogs.teamb.com/rudyvelthuis/2006/07/29/26308
    val f = new File(current.path, "/conf/quotes.txt")
    val lines = org.apache.commons.io.IOUtils.readLines(new FileInputStream(f), "utf-8").asScala
    val quotes = new ArrayBuffer[String]()
    val sb = new StringBuilder()
    try {
      for (line <- lines) {
        if (line == ".") {
          quotes += sb.result()
          sb.clear()
        } else {
          sb.append(line).append("\n")
        }
      }
    } catch {
      case t => t.printStackTrace()
    }
    quotes.toList
  }

  def randomQuote() = {
    val index = java.lang.Math.random() * quotes.size + 1
    quotes(index.toInt)
  }

}
