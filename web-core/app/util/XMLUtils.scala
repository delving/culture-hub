package util

import models.Namespace

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object XMLUtils {

  def namespacesToString(namespaces: Map[String, String]): String = namespaces.map { ns =>
    if (ns._1.isEmpty) {
      """xmlns="%s"""".format(ns._2)
    } else {
      """xmlns:%s="%s"""".format(ns._1, ns._2)
    }
  }.mkString(" ")

  def namespacesToString(namespaces: Seq[Namespace]): String = namespacesToString(
    namespaces.map(ns => (ns.prefix -> ns.uri)).toMap
  )

}