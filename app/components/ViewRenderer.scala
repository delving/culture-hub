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

package components

import java.io.File
import groovy.util.Node
import xml.XML
import collection.mutable.{HashMap, ArrayBuffer}
import groovy.xml.QName
import collection.mutable.Stack

/**
 * View Rendering mechanism. Reads a ViewDefinition from a given record definition, and applies it onto the input data (a node tree).
 *
 * TODO: separate definition parsing and output generation
 * TODO: complex list-s
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ViewRenderer {

  def renderView(recordDefinition: File, view: String, record: Node): RenderNode = {

    def throwUnknownElement(e: scala.xml.Node) {
      throw new RuntimeException("Unknown element '%s'".format(e))
    }

    val result = RenderNode("root")

    val treeStack = Stack(result)

    def enter(node: scala.xml.Node, nodeType: String, attr: (String, Any)*)(block: scala.xml.Node => Unit) {
      println("Entered " + node.label)
      val entered = RenderNode(nodeType)
      attr foreach {
        entered addAttr _
      }
      treeStack.head += entered
      treeStack.push(entered)
      node.child foreach {
        n =>
          println("Node " + n)
          if (n.label != "#PCDATA") block(n)
      }
      treeStack.pop()
    }

    def append(nodeType: String, attr: (String, Any)*)(block: RenderNode => Unit) {
      val newNode = RenderNode(nodeType)
      attr foreach {
        newNode addAttr _
      }
      treeStack.head += newNode
      treeStack.push(newNode)
      block(newNode)
      treeStack.pop()
    }

    val xml = XML.loadFile(recordDefinition)
    (xml \ "views" \ "view").filter(v => (v \ "@name").text == view).headOption match {
      case Some(viewDefinition) =>

        viewDefinition.child.iterator.filterNot(_.label == "#PCDATA") foreach {
          r =>
            r.label match {
              case "row" =>
                enter(r, "row") {
                  c =>
                    c.label match {
                      case "column" =>
                        enter(c, "column", ("id" -> (c \ "@id").text)) {
                          e => e.label match {
                            case "field" =>

                              // initialize the field and its meta-data
                              append("field",
                                ("label", (e \ "@label").text),
                                ("queryLink", {
                                  val l = (e \ "@queryLink").text
                                  if (l.isEmpty) false else l.toBoolean
                                })) {
                                field =>
                                // fetch the unique field value
                                  val values = fetchPaths(record, (e \ "@path").text.split(",").map(_.trim).toList)
                                  field += RenderNode("text", values.headOption)
                              }

                            case "list" =>
                              append("list",
                                ("label", (e \ "@label").text),
                                ("queryLink", {
                                  val l = (e \ "@queryLink").text
                                  if (l.isEmpty) false else l.toBoolean
                                }),
                                ("type", (e \ "@type").text),
                                ("separator", (e \ "@separator").text)
                              ) {
                                list =>

                                  if (e.child.isEmpty) {

                                    // first case: we have a closed list, thus assuming we only want to loop over the elements given in the list
                                    // e.g.
                                    //       <list type="concatenated" label="metadata.dc.format" path="dc_format, dcterms_extent" separator=", " />

                                    val values = fetchPaths(record, (e \ "@path").text.split(",").map(_.trim).toList)
                                    values foreach {
                                      v => list += RenderNode("text", Some(v))
                                    }
                                  } else {
                                    throw new RuntimeException("Complex <list> not yet implemented.")
                                  }


                              }
                            case u => throwUnknownElement(e)
                          }
                        }
                      case u => throwUnknownElement(c)
                    }
                }
              case u => throwUnknownElement(r)
            }
        }

      case None => throw new RuntimeException("Could not find view definition '%s' in file '%s'".format(view, recordDefinition.getAbsolutePath))
    }

    result
  }

  private def fetchPaths(rootNode: Node, paths: Seq[String]): Seq[String] = {
    (for (path <- paths) yield {
      // basic traversal assuming we only have single elements all the way, until the last element which may be multiple
      fetch(rootNode, path.split("/"), 0)
    }).flatten
  }

  private def fetch(n: Node, p: Array[String], level: Int): List[String] = {
    import scala.collection.JavaConversions._

    // don't we all love type-unsafe APIs?
    val children = n.children().filter(_.isInstanceOf[Node]).map(_.asInstanceOf[Node])

    if (level + 1 < p.length) {
      val t = children.find(_.name().asInstanceOf[QName].getQualifiedName == p(level)).getOrElse(return List.empty)
      fetch(t, p, level + 1)
    } else {
      children.filter(_.name().asInstanceOf[QName].getQualifiedName == p(level)).map(_.text()).toList
    }
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

  def addAttr(element: (String, Any)) {
    attributes += element
  }

  def text: String = value.getOrElse("")

  override def toString = """
  NodeType: %s
  Value: %s
  Attributes: %s
  Content: %s
  """.format(nodeType, value, attributes.toString(), content.map(_.nodeType))
}