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
   * @param removeNamespacePrefix removes the XML namespace prefix
   * @param sequences paths of objects that should only contain arrays
   * @return a string formatted as JSON
   */
  def toJson(xml: NodeSeq, escapeNamespaces: Boolean = false, removeNamespacePrefix: Boolean = false, sequences: Seq[List[String]] = List.empty): JValue = {

    // because lift-json doesn't group XML nodes that have the same name under an array if there's more than one potential group, we do it ourselves here
    // NOTE: lift-json is flattening leaf elements, i.e. considering
    // <a><foo id="1">x</foo></a> -> {"a":{"foo":{"foo":"x","id":"1"}}} vs
    // <a><foo id="1">x</foo></a> -> {"a":{"foo":"x","id":"1"}}
    // the second case happens.
    // this is leading to trouble if you've got more than one "id" on the same level, e.g.
    // <a><foo id="1">x</foo><bar id="2">bla</bar></a> -> {"a":{"foo":"x","bar":"bla","id":["1","2"]}}
    // actually in the original transformation, "1" gets lost, thanks to our systematic grouping we keep it - but it is confusing nonetheless
    val json = groupingTraversal(Xml.toJson(xml))

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
    val js = if (escapeNamespaces || removeNamespacePrefix) {
      mutated.transform {
        case JField(name, v) if escapeNamespaces && !removeNamespacePrefix && name.contains(":") => JField(name.replaceAll(":", "_"), v)
        case JField(name, v) if removeNamespacePrefix && name.contains(":") => JField(name.substring(name.indexOf(":") + 1), v)
      }
    } else {
      mutated
    }

    js
  }

  def renderToJson(xml: NodeSeq, escapeNamespaces: Boolean = false, sequences: Seq[List[String]] = List.empty): String = {
    compact(render(toJson(xml, escapeNamespaces, sequences = sequences)))

  }

  private def groupingTraversal(node: JValue): JValue = node match {
    case JObject(obj: Seq[JField]) =>
      val capturedOrder = obj.map(_.name).zipWithIndex.toMap[String, Int]
      val groupedFields = obj.groupBy(_.name)
      val grouped = groupedFields map {
        g =>
          if (g._2.length > 1) {
            JField(g._1, JArray(g._2.map(f => f.value)).map(groupingTraversal(_)))
          } else {
            JField(g._1, g._2.head.value.map(groupingTraversal(_)))
          }
      }
      val reordered = grouped.toList.sortWith((one, two) => capturedOrder(one.name) < capturedOrder(two.name))
      JObject(reordered)
    case other @ _ => other
  }

}
