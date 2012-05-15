package util

import xml.NodeSeq
import net.liftweb.json._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Json {

  /**
   * Turns a scala XML node into a JSON string
   *
   * @param xml the input xml document
   * @param removeNamespaces removes XML namespace declarations
   * @param sequences field names that are sequences, and should be generated as array even when there's only a single element
   * @return a string formatted as JSON
   */
  def toJson(xml: NodeSeq, removeNamespaces: Boolean = false, sequences: Seq[String] = List.empty): String = {
    val json = Xml.toJson(xml) map {
        case JField(name: String, x: JObject) =>
          if(sequences.contains(name))
            JField(name, JArray(x :: Nil))
          else
            x
        case x => x
    }
    val js = if(removeNamespaces) {
      json.transform {
        case JField(name, v) if(name.contains(":")) => JField(name.split(":").tail.mkString, v)
      }
    } else json

    Printer.pretty(render(js))
  }


}
