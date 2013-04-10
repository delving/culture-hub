package util

import scala.xml._
import scala.collection.immutable.Stack

/**
 * Prototype for EAD tree simplification
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object EADSimplifier {

  def simplify(source: NodeSeq) = {

    val title = (source \ "eadheader" \ "filedesc" \ "titlestmt" \ "titleproper").text

    val id = (source \ "eadheader" \ "eadid" \ "@identifier").text

    val simplifiedArchDesc = simplifyArchDesc(source \ "archdesc")

    val firstCs = source \ "archdesc" \ "dsc" \ "c"

    val simplifiedCs = simplifyC(firstCs, Stack("/ead/archdesc/dsc/c"))

    <node>
      <id>{ id }</id>
      <title>{ title }</title>
      <key>/</key>
      <archdesc>
        { simplifiedArchDesc }
      </archdesc>
      { simplifiedCs }
    </node>

  }

  def simplifyArchDesc(node: NodeSeq): Seq[Node] = {

    def did(elt: String) = (node \ "did" \ elt).text

    <did_unitid>{ did("unitid") }</did_unitid>
    <did_unittitle>{ did("unittitle") }</did_unittitle>
    <did_unitdate>{ did("unitdate") }</did_unitdate>
    <did_origination_corpname>{ (node \ "did" \ "origination" \ "corpname").text }</did_origination_corpname>
    <odd>{ (node \ "odd" \ "p").text }</odd>
    <arrangement>{ (node \ "arrangement" \ "p").text }</arrangement>
  }

  def simplifyC(nodeSeq: Seq[Node], path: Stack[String]): Seq[Node] = {
    nodeSeq.zipWithIndex map {
      pair =>
        val node = pair._1
        val index = pair._2
        node match {
          case n if n.label == "c" =>
            val kids = n \ "c"
            <node>
              <title>{ (n \ "did" \ "unittitle").text }</title>
              <id>{ (n \ "did" \ "unitid").text }</id>
              <date>{ (n \ "did" \ "unitdate").text }</date>
              <odd>{ (n \ "odd" \ "p").text }</odd>
              <otherfindaid>{ (n \ "otherfindaid" \ "p").text }</otherfindaid>
              <key>{ path.reverse.mkString("/") + s"/c/c[$index]" }</key>
              { simplifyC(kids, path push n.label + s"[$index]") }
            </node>
        }
    }
  }

}
