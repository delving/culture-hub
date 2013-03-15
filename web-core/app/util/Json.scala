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
   * @param escapeNamespaces escapes XML namespace declarations
   * @param sequences paths of objects that should only contain arrays
   * @return a string formatted as JSON
   */
  def toJson(xml: NodeSeq, escapeNamespaces: Boolean = false, sequences: Seq[List[String]] = List.empty): String = {

    // because lift-json doesn't group XML nodes that have the same name under an array if there's more than one potential group, we do it ourselves here
    val json = Xml.toJson(xml) transform {
      case JObject(obj: List[JField]) => {
        val capturedOrder = obj.map(_.name).zipWithIndex.toMap[String, Int]
        val groupedFields = obj.groupBy(_.name)
        val grouped = groupedFields map {
          g =>
            if (g._2.length > 1) {
              JField(g._1, JArray(g._2.map(f => f.value)))
            } else {
              JField(g._1, g._2.head.value)
            }
        }
        val reordered = grouped.toList.sortWith((one, two) => capturedOrder(one.name) < capturedOrder(two.name))
        JObject(reordered)
      }
    }

    // when we are given a set of paths of JObjects for which all children should have arrays as values, mutate accordingly
    var mutated = json
    for (s <- sequences) {
      val value = s.foldLeft(mutated) { _ \ _ } match {
        case JObject(obj: List[JField]) =>
          JObject(obj.map(f => JField(f.name, if (f.value.isInstanceOf[JArray]) f.value else JArray(f.value :: Nil))))
        case other => other
      }
      mutated = mutated.replace(s, value)
    }

    // escape the namespace prefixes so that the JSON values can be easily accessed
    val js = if (escapeNamespaces) {
      mutated.transform {
        case JField(name, v) if (name.contains(":")) => JField(name.replaceAll(":", "_"), v)
      }
    } else {
      mutated
    }

    compact(render(js))
  }

}
