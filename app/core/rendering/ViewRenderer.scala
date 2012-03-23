/*
 * Copyright 2012 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package core.rendering

import scala.xml.Node
import xml.XML
import collection.mutable.{HashMap, ArrayBuffer}
import collection.mutable.Stack
import models.GrantType
import play.api.Logger
import java.io.File
import org.w3c.dom.Document
import play.libs.XPath

/**
 * View Rendering mechanism. Reads a ViewDefinition from a given record definition, and applies it onto the input data (a node tree).
 *
 * TODO: separate definition parsing and output generation
 * TODO: complex list-s
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ViewRenderer {

  val log = Logger("CultureHub")

  def renderView(recordDefinition: File, view: String, record: Document, userGrantTypes: List[GrantType], namespaces: Map[String, String]): RenderNode = {
    val prefix = recordDefinition.getName.substring(0, recordDefinition.getName.indexOf("-"))
    val xml = XML.loadFile(recordDefinition)
    (xml \ "views" \ "view").filter(v => (v \ "@name").text == view).headOption match {
      case Some(viewDefinition) => renderView(prefix, viewDefinition, view, record, userGrantTypes, namespaces)
      case None => throw new RuntimeException("Could not find view definition '%s' in file '%s'".format(view, recordDefinition.getAbsolutePath))
    }
  }

  def renderView(prefix: String, viewDefinition: Node, view: String, record: Document, userGrantTypes: List[GrantType], namespaces: Map[String, String]): RenderNode = {

    val result = RenderNode("root")
    val treeStack = Stack(result)
    val root = viewDefinition.child.iterator.filterNot(_.label == "#PCDATA").toList.head
    walk(root)

    implicit def richerNode(n: Node) = new {
        def attr(name: String) = {
          val sel = "@" + name
          (n \ sel).text
        }
      }

    def walk(node: Node) {

      node.foreach {
        n =>
          log.debug("Node " + n)
          if (n.label != "#PCDATA") {

            // common attributes
            val label = n.attr("label")
            val role = n.attr("role")
            val path = n.attr("path")
            val queryLink = {
              val l = n.attr("queryLink")
              if (l.isEmpty) false else l.toBoolean
            }

            val roleList = role.split(",").map(_.trim).filterNot(_.isEmpty).toList

            n.label match {

              case "row" => enterOne(n, "row")
              case "column" => enterOne(n, "column", 'id -> n.attr("id"))
              case "field" =>
                if (hasAccess(roleList)) {
                  appendOne("field", 'label -> label, 'queryLink -> queryLink) {
                    field =>
                      val values = fetchPaths(record, path.split(",").map(_.trim).toList, namespaces)
                      field += RenderNode("text", values.headOption)
                  }
                }
              case "enumeration" =>
                if (hasAccess(roleList)) {

                  appendOne("enumeration", 'label -> label, 'queryLink -> queryLink, 'type -> n.attr("type"), 'separator -> n.attr("separator")) {
                    list =>

                      if (!n.child.isEmpty) {
                        throw new RuntimeException("An enumeration cannot have child elements!")
                      }

                      val values = fetchPaths(record, path.split(",").map(_.trim).toList, namespaces)
                      values foreach {
                        v => list += RenderNode("text", Some(v))
                      }
                  }
                }

              case u@_ => throw new RuntimeException("Unknown element '%s'".format(u))


            }
          }

      }
    }

    /** appends a new RenderNode to the result tree and walks one level deeper **/
    def enterOne(node: Node, nodeType: String, attr: (Symbol, Any)*) {
      log.debug("Entered " + node.label)
      val entered = RenderNode(nodeType)
      attr foreach {
        entered addAttr _
      }
      treeStack.head += entered
      treeStack.push(entered)
      node.child foreach {
        n =>
          log.debug("Node " + n)
          walk(n)
      }
      treeStack.pop()

    }

    /** appends a new RenderNode to the result tree and performs an operation on it **/
    def appendOne(nodeType: String, attr: (Symbol, Any)*)(block: RenderNode => Unit) {
      val newNode = RenderNode(nodeType)
      attr foreach {
        newNode addAttr _
      }
      treeStack.head += newNode
      treeStack.push(newNode)
      block(newNode)
      treeStack.pop()
    }

    def hasAccess(roles: List[String]) = {
      roles.isEmpty || (userGrantTypes.exists(gt => roles.contains(gt.key) && gt.origin == prefix) || userGrantTypes.exists(gt => gt.key == "own" && gt.origin == "System"))
    }

    result

  }


  private def fetchPaths(record: Document, paths: Seq[String], namespaces: Map[String, String]): Seq[String] = {

    import collection.JavaConverters._

    (for (path <- paths) yield {
      XPath.selectText(path, record, namespaces.asJava)
    })
  }

}

/**
 * A node used to hold the structure to be rendered
 */
case class RenderNode(nodeType: String, value: Option[String] = None) {

  private val contentBuffer = new ArrayBuffer[RenderNode]
  private val attributes = new HashMap[String, Any]

  def content: List[RenderNode] = contentBuffer.toList

  def +=(node: RenderNode) {
    contentBuffer += node
  }

  def attr(key: String) = attributes(key)

  def addAttr(key: String, value: AnyRef) = attributes + (key -> value)

  def addAttr(element: (Symbol, Any)) {
    attributes += (element._1.name -> element._2)
  }

  def text: String = value.getOrElse("")

  override def toString = """
  NodeType: %s
  Value: %s
  Attributes: %s
  Content: %s
  """.format(nodeType, value, attributes.toString(), content.map(_.nodeType))
}