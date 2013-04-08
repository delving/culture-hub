package util

import scala.xml._
import java.io.File
import scala.collection.immutable.Stack

/**
 * Prototype for EAD tree simplification
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object EADSimplifier {

  def simplify(source: NodeSeq) = {

    val title = (source \ "eadheader" \ "filedesc" \ "titlestmt" \ "titleproper").text

    val dscRoot = source \ "archdesc" \ "dsc"

    val firstCs = dscRoot \ "c"

    val res = simplifyC(firstCs, Stack("/ead/archdesc/dsc"))

    val r = <all>{ res }</all>

    r

  }

  def simplifyC(nodeSeq: Seq[Node], path: Stack[String]): Seq[Node] = {
    nodeSeq.zipWithIndex map {
      pair =>
        val node = pair._1
        val index = pair._2
        node match {
          case n if n.label == "c" =>
            val title = (n \ "did" \ "unittitle").text
            val kids = n \ "c"
            <node>
              <title>{ title }</title>
              <key>{ path.reverse.mkString("/") + s"/c/c[$index]" }</key>
              { simplifyC(kids, path push n.label + s"[$index]") }
            </node>
        }
    }
  }

}
