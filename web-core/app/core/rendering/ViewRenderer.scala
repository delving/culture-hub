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

import models.{ OrganizationConfiguration, Role }
import play.libs.XPath
import collection.JavaConverters._
import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream
import play.api.i18n.{ Messages, Lang }
import org.apache.commons.lang.StringEscapeUtils
import xml.{ NodeSeq, Node, XML }
import play.api.{ Play, Logger }
import play.api.Play.current
import org.w3c.dom.{ Node => WNode, NodeList, Text }
import java.net.URLEncoder
import collection.mutable.{ HashMap, ArrayBuffer, Stack }
import util.XPathWorking

/**
 * View Rendering mechanism. Reads a ViewDefinition from a given record definition, and applies it onto the input data (a node tree).
 *
 * TODO refactor this:
 * - the tree walking and building methods should be encapsulated in their own TreeWalker class which gets initialized with an empty stack
 * - since there is a number of common attributes shared amongst elements (especially in the ViewRendering DSL), those elements
 *   should be resolved upfront and inherited by all special cases. so a more generic way of declaring elements or their results needs to be put into place
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ViewRenderer {

  def fromDefinition(schema: String, viewType: ViewType)(implicit configuration: OrganizationConfiguration) = {
    val definition = getViewDefinition(schema, viewType)
    if (definition.isDefined) {
      Some(new ViewRenderer(schema, viewType, configuration))
    } else {
      None
    }
  }

  def getViewDefinition(schema: String, viewType: ViewType): Option[Node] = {
    val definitionResource = Play.application.resource("/%s-view-definition.xml".format(schema))
    if (!definitionResource.isDefined) {
      None
    } else {
      val xml = XML.load(definitionResource.get)
      (xml \ "view").filter(v => (v \ "@name").text == viewType.name).headOption
    }
  }

}

class ViewRenderer(val schema: String, viewType: ViewType, configuration: OrganizationConfiguration) {

  implicit class RichNodeList(nodeList: NodeList) {
    def asIterator: Iterator[org.w3c.dom.Node] = new Iterator[org.w3c.dom.Node] {
      var index = 0

      def hasNext: Boolean = index < nodeList.getLength

      def next(): org.w3c.dom.Node = {
        val item = nodeList.item(index)
        index = index + 1
        item
      }
    }
  }

  val log = Logger("CultureHub")

  val viewDef: Option[Node] = ViewRenderer.getViewDefinition(schema, viewType)

  val dbFactory = DocumentBuilderFactory.newInstance
  dbFactory.setNamespaceAware(true)
  val dBuilder = dbFactory.newDocumentBuilder

  def renderRecord(record: String, userRoles: Seq[Role], namespaces: Map[String, String], lang: Lang, parameters: Map[String, Seq[String]] = Map.empty): RenderedView = {
    viewDef match {
      case Some(viewDefinition) =>
        log.debug("Starting view rendering")
        log.debug("Namespaces: " + namespaces.toString)
        renderRecordWithView(schema, viewType, viewDefinition, record, userRoles, namespaces, lang, parameters)
      case None => throw new RuntimeException("Could not find view definition '%s' for schema '%s'".format(viewType.name, schema))
    }
  }

  def renderRecordWithView(prefix: String,
    viewType: ViewType,
    viewDefinition: Node,
    rawRecord: String,
    userRoles: Seq[Role],
    namespaces: Map[String, String],
    lang: Lang,
    parameters: Map[String, Seq[String]]): RenderedView = {

    val record = dBuilder.parse(new ByteArrayInputStream(rawRecord.getBytes("utf-8")))

    val result = RenderNode("root", None, true)
    var shortcutResult: Option[RenderedView] = None
    val treeStack = Stack(result)
    var stackPath = new ArrayBuffer[String]

    def defaultParent = if (treeStack.head.nodeType == "list") treeStack.head.parent else treeStack.head

    val arrays = new ArrayBuffer[List[String]] // paths that are arrays of repeated elements

    val root = viewDefinition
    walk(root, record)

    implicit def richerNode(n: Node) = new {
      def attr(name: String) = {
        val sel = "@" + name
        (n \ sel).text
      }
    }

    def push(node: RenderNode) {
      treeStack.push(node)
      stackPath += node.nodeType
    }

    def pop() {
      treeStack.pop()
      if (stackPath.length > 0) stackPath = stackPath.dropRight(1)
    }

    def walk(viewDefinitionNode: Node, dataNode: WNode) {

      viewDefinitionNode.foreach {
        n =>
          log.trace("Node " + n)
          val ifExpr = n.attr("if")
          val ifNotExpr = n.attr("ifNot")

          val ifValue = if (!ifExpr.isEmpty) {
            val v = XPathWorking.selectText(ifExpr, dataNode, namespaces.asJava)
            v != null && !v.isEmpty
          } else {
            true
          }
          val ifNotValue = if (!ifNotExpr.isEmpty) {
            val v = XPathWorking.selectText(ifNotExpr, dataNode, namespaces.asJava)
            v != null && !v.isEmpty
          } else {
            false
          }

          if (n.label != "#PCDATA" && ifValue && !ifNotValue) {

            // common attributes
            val label = if (n.attr("labelExpr").isEmpty) n.attr("label") else XPathWorking.selectText(n.attr("labelExpr"), dataNode, namespaces.asJava)

            val role = n.attr("role")
            val path = n.attr("path")
            val value = n.attr("value")

            val queryLink = {
              val l = n.attr("queryLink")
              if (l.isEmpty) false else l.toBoolean
            }

            val roleList = role.split(",").map(_.trim).filterNot(_.isEmpty).toList

            n.label match {

              // ~~~ generic elements

              case "view" => enterAndAppendOne(n, dataNode, "root", true)

              case "elem" => withAccessControl(roleList) {
                role =>

                  val name = n.attr("name")
                  val prefix = n.attr("prefix")

                  val elemName = if (prefix.isEmpty) name else prefix + ":" + name

                  val isArray = n.attr("isArray") == "true"

                  val attrs = fetchNestedAttributes(n, dataNode)

                  val elemValue = if (!n.attr("expr").isEmpty) {
                    Some(XPathWorking.selectText(n.attr("expr"), dataNode, namespaces.asJava))
                  } else if (!n.attr("value").isEmpty) {
                    Some(n.attr("value"))
                  } else {
                    None
                  }

                  val r = RenderNode(nodeType = elemName, value = elemValue, isArray = isArray)
                  r.addAttrs(attrs)

                  if (elemValue.isDefined && n.child.isEmpty) {
                    appendNode(r)
                  } else if (!n.child.isEmpty) {
                    enterAndAppendNode(n, dataNode, r)
                  }

              }

              case "list" => withAccessControl(roleList) {
                role =>

                  val name = n.attr("name")
                  val prefix = n.attr("prefix")
                  val distinct = n.attr("distinct")

                  val listName = if (name.isEmpty && prefix.isEmpty) {
                    "list"
                  } else {
                    if (prefix.isEmpty) {
                      name
                    } else {
                      prefix + ":" + name
                    }
                  }

                  val attrs = fetchNestedAttributes(n, dataNode)

                  val list = RenderNode(nodeType = listName, value = None, isArray = true)
                  list.addAttrs(attrs)

                  list.parent = defaultParent
                  treeStack.head += list
                  push(list)

                  arrays += stackPath.toList.drop(1) // drop root

                  val allChildren = XPathWorking.selectNodes(path, dataNode, namespaces.asJava).asIterator.toSeq
                  val children = if (distinct == "name") {
                    val distinctNames = allChildren.map(c => c.getPrefix + c.getLocalName).distinct
                    distinctNames.flatMap(n => allChildren.find(c => (c.getPrefix + c.getLocalName) == n))
                  } else {
                    allChildren
                  }

                  children.foreach {
                    child =>
                      enterNode(n, child)
                  }
                  pop
              }

              case "attrs" => // this is handled by elem below

              case "verbatim" =>
                // shortcut everything. pull out XML directly, and use lift-json to turn it into JSON. not fit for HTML
                shortcutResult = Some(new RenderedView {
                  def toXmlString: String = rawRecord

                  def toJson: String = util.Json.toJson(xml = toXml, escapeNamespaces = true, sequences = arrays.toList)

                  def toXml: NodeSeq = XML.loadString(rawRecord)

                  def toViewTree: RenderNode = null
                })

              // ~~~ legacy support

              case "auto-field" =>
                val current = XPathWorking.selectNode(".", dataNode, namespaces.asJava)
                val renderNode = RenderNode(current.getNodeName, Some(current.getTextContent))
                appendNode(renderNode)

              case "auto-layout-field" =>
                val current = XPathWorking.selectNode(".", dataNode, namespaces.asJava)
                val internationalizationKey = "metadata." + current.getNodeName.replaceAll(":", ".")

                val renderNode = RenderNode("field", None)
                renderNode += RenderNode("name", Some(current.getNodeName.replaceAll(":", "_")))
                renderNode += RenderNode("i18n", Some(Messages(internationalizationKey)(lang)))
                appendNode(renderNode)

              // ~~~ view definition elements

              case "row" => enterAndAppendOne(n, dataNode, "row", true, 'proportion -> n.attr("proportion"))
              case "column" => enterAndAppendOne(n, dataNode, "column", true, 'proportion -> n.attr("proportion"))
              case "container" => withAccessControl(roleList) {
                role =>
                  enterAndAppendOne(n, dataNode, "container", true, 'id -> n.attr("id"), 'class -> n.attr("class"), 'title -> n.attr("title"), 'label -> label, 'type -> n.attr("type"), 'role -> role.map(_.getDescription(lang)).getOrElse(""))
              }
              case "image" => withAccessControl(roleList) {
                role =>
                  val value = fetchPaths(dataNode, path.split(",").map(_.trim).toList, namespaces).headOption.map {
                    url =>
                      if (configuration.objectService.imageCacheEnabled) {
                        "/image/cache?id=%s".format(URLEncoder.encode(url, "utf-8"))
                      } else {
                        url
                      }
                  }
                  append("image", value, 'title -> n.attr("title"), 'type -> n.attr("type"), 'role -> role.map(_.getDescription(lang)).getOrElse("")) { renderNode => }
              }
              case "field" => withAccessControl(roleList) {
                role =>
                  val v = if (!value.isEmpty)
                    Some(evaluateParamExpression(value, parameters))
                  else
                    fetchPaths(dataNode, path.split(",").map(_.trim).toList, namespaces).headOption

                  append("field", v, 'label -> label, 'queryLink -> queryLink, 'role -> role.map(_.getDescription(lang)).getOrElse("")) { renderNode => }
              }
              case "enumeration" => withAccessControl(roleList) {
                role =>
                  appendSimple("enumeration", 'label -> label, 'queryLink -> queryLink, 'separator -> n.attr("separator"), 'role -> role.map(_.getDescription(lang)).getOrElse("")) {
                    list =>

                      if (!n.child.isEmpty) {
                        throw new RuntimeException("An enumeration cannot have child elements!")
                      }

                      val values = fetchPaths(dataNode, path.split(",").map(_.trim).toList, namespaces)
                      values foreach {
                        v => list += RenderNode("_text_", Some(v))
                      }
                  }
              }
              case "link" =>
                val urlExpr = n.attribute("urlExpr").map(e => XPathWorking.selectText(e.text, dataNode, namespaces.asJava))
                val urlValue = n.attr("urlValue")

                val url: String = evaluateParamExpression(urlValue, parameters) + urlExpr.getOrElse("")

                val text: String = if (n.attribute("textExpr").isDefined) {
                  val textValues = fetchPaths(dataNode, n.attr("textExpr").split(",").map(_.trim).toList, namespaces)
                  val sep = if (n.attribute("separator").isDefined) n.attr("separator") else ", "
                  textValues.mkString(sep)
                } else if (n.attribute("textValue").isDefined) {
                  evaluateParamExpression(n.attr("textValue"), parameters)
                } else {
                  ""
                }

                appendSimple("link", 'url -> url, 'text -> text, 'label -> label, 'type -> n.attr("type")) { node => }

              case u @ _ => throw new RuntimeException("Unknown element '%s'".format(u))

            }
          }

      }
    }

    def fetchNestedAttributes(n: Node, dataNode: WNode): Map[String, String] = {
      val attrDefinitions = n \ "attrs" \ "attr"

      (for (a: Node <- attrDefinitions) yield {
        val name = a.attr("name")
        if (name.isEmpty) {
          throw new RuntimeException("Attribute must have a name")
        }
        val prefix = a.attr("prefix")

        val attrName = if (prefix.isEmpty) name else prefix + ":" + name
        val attrValue = if (!a.attr("expr").isEmpty) {
          XPathWorking.selectText(a.attr("expr"), dataNode, namespaces.asJava)
        } else if (!a.attr("value").isEmpty) {
          a.attr("value")
        } else {
          throw new RuntimeException("Attribute %s without value or expr provided".format(name))
        }

        (attrName -> attrValue)
      }).toMap
    }

    /** appends a new RenderNode to the result tree and walks one level deeper **/
    def enterAndAppendOne(viewDefinitionNode: Node, dataNode: WNode, nodeType: String, isArray: Boolean = false, attr: (Symbol, Any)*) {
      val newRenderNode = RenderNode(nodeType = nodeType, value = None, isArray = isArray)
      attr foreach {
        newRenderNode addAttr _
      }
      enterAndAppendNode(viewDefinitionNode, dataNode, newRenderNode)
    }

    def enterAndAppendNode(viewDefinitionNode: Node, dataNode: WNode, renderNode: RenderNode) {
      log.trace("Entered " + viewDefinitionNode.label)
      renderNode.parent = defaultParent

      treeStack.head += renderNode
      push(renderNode)
      viewDefinitionNode.child foreach {
        n =>
          log.trace("Node " + n)
          walk(n, dataNode)
      }
      pop
    }

    /** enters a view definition node, but without appending a new node on the the current tree **/
    def enterNode(viewDefinitionNode: Node, dataNode: WNode) {
      log.trace("Entered " + viewDefinitionNode.label)
      viewDefinitionNode.child foreach {
        n =>
          log.trace("Node " + n)
          walk(n, dataNode)
      }

    }

    /** appends a new RenderNode without content to the result tree and performs an operation on it **/
    def appendSimple(nodeType: String, attr: (Symbol, Any)*)(block: RenderNode => Unit) {
      append(nodeType, None, attr: _*)(block)
    }

    /** appends a new RenderNode to the result tree and performs an operation on it **/
    def append(nodeType: String, text: Option[String] = None, attr: (Symbol, Any)*)(block: RenderNode => Unit) {
      val newNode = RenderNode(nodeType, text)
      newNode.parent = defaultParent
      attr foreach {
        newNode addAttr _
      }
      treeStack.head += newNode
      push(newNode)
      block(newNode)
      pop
    }

    /** simply appends a node to the current tree head **/
    def appendNode(node: RenderNode) {
      node.parent = defaultParent
      treeStack.head += node
    }

    def withAccessControl(roles: List[String])(block: Option[Role] => Unit) {
      if (roles.isEmpty) {
        block(None)
      } else if (userRoles.contains(Role.OWN)) {
        block(Some(Role.OWN))
      } else if (userRoles.exists(gt => roles.contains(gt.key))) {
        block(userRoles.find(gt => roles.contains(gt.key)).headOption)
      } else {
        // though luck, man
      }
    }

    if (shortcutResult.isDefined) {
      shortcutResult.get
    } else {
      NodeRenderedView(viewType, prefix, result.content.head, arrays.toList)
    }

  }

  def fetchPaths(dataNode: Object, paths: Seq[String], namespaces: Map[String, String]): Seq[String] = {
    (for (path <- paths) yield {
      XPathWorking.selectNodes(path, dataNode, namespaces.asJava).asIterator.toSeq.flatMap {
        node =>
          var rnode = node
          try {
            if (rnode == null) {
              None
            } else {
              if (!(rnode.isInstanceOf[Text])) {
                rnode = node.getFirstChild
              }
              if (!(rnode.isInstanceOf[Text])) {
                None
              } else {
                Some((rnode.asInstanceOf[Text]).getData)
              }
            }
          } catch {
            case e: Exception => {
              throw new RuntimeException(e)
              None
            }
          }
      }

    }).flatten
  }

  def evaluateParamExpression(value: String, parameters: Map[String, Seq[String]]): String = {
    """\$\{(.*)\}""".r.replaceAllIn(value, m => parameters.get(m.group(1)).map(_.headOption.getOrElse("")).getOrElse {
      log.warn("Could not find value for parameter %s while rendering view %s".format(m.group(1), viewType.name))
      ""
    })
  }

}

abstract class RenderedView {
  def toXml: NodeSeq
  def toXmlString: String
  def toJson: String
  def toViewTree: RenderNode
}

case class NodeRenderedView(viewType: ViewType, schemaPrefix: String, viewTree: RenderNode, sequences: Seq[List[String]]) extends RenderedView {

  def toXml = XML.loadString(toXmlString)

  def toXmlString = RenderNode.toXMLString(viewTree)

  def toJson = RenderNode.toJson(viewTree, sequences)

  def toViewTree = viewTree
}

/**
 * A node used to hold the structure to be rendered
 */
case class RenderNode(nodeType: String, value: Option[String] = None, isArray: Boolean = false, isFlatArray: Boolean = false) {

  private val contentBuffer = new ArrayBuffer[RenderNode]
  private val attributes = new HashMap[String, Any]

  var parent: RenderNode = null

  def content: List[RenderNode] = contentBuffer.toList

  def +=(node: RenderNode) {
    if (contentBuffer.exists(r =>
      (r.nodeType == node.nodeType)
        && !isArray
        && node.nodeType != "_text_")) {
      throw new RuntimeException("In node %s: you cannot have child elements with the same name (%s) without explicitely declaring the container element to be an array!".format(nodeType, node.nodeType))
    }
    contentBuffer += node
  }

  def attr(key: String) = if (attributes.contains(key)) attributes(key) else ""

  def addAttr(key: String, value: AnyRef) = {
    if (!value.toString.trim.isEmpty) {
      attributes + (key -> value)
    }
  }

  def addAttr(element: (Symbol, Any)) {
    if (!element._2.toString.trim.isEmpty) {
      attributes += (element._1.name -> element._2)
    }
  }

  def addAttrs(attrs: Map[String, String]) {
    attributes ++= attrs.filterNot(_._2.isEmpty)
  }

  def attributesAsXmlString: String = attributes.map(a => a._1 + "=\"" + a._2.toString + "\"").mkString(" ")

  def text: String = value.getOrElse("")

  def isLeaf: Boolean = content.isEmpty

  override def toString = """[%s] - %s - %s""".format(nodeType, value, attributes.toString())
}

case object RenderNode {

  def visit(n: RenderNode) {
    val sb = new StringBuilder()
    visit(n, 0, sb)
    println(sb.toString())
  }

  def visit(n: RenderNode, level: Int = 0, sb: StringBuilder) {
    for (i <- 0 to level) sb.append(" ")
    sb.append(n.toString)
    sb.append("\n")
    for (c <- n.content) {
      visit(c, level + 1, sb)
    }
  }

  def toXMLString(n: RenderNode): String = {
    val sb = new StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n")
    visitXml(n, 0, "", sb)
    sb.toString()
  }

  def visitXml(n: RenderNode, level: Int = 0, indent: String = "", sb: StringBuilder) {

    if (n.nodeType == "root") {
      for (c <- n.content) {
        visitXml(c, level, "", sb)
      }
    } else {
      val indentation: String = (for (i <- 0 to level) yield " ").mkString + indent

      sb.append(indentation)

      if (n.isFlatArray) {
        n.content.foreach {
          c =>
            sb.append("<%s%s>".format(n.nodeType, if (n.attributesAsXmlString.isEmpty) "" else " " + n.attributesAsXmlString))
            sb.append(StringEscapeUtils.escapeXml(n.text))
            sb.append("</%s>".format(n.nodeType))
            sb.append("\n")
        }
      } else {
        sb.append("<%s%s>".format(n.nodeType, if (n.attributesAsXmlString.isEmpty) "" else " " + n.attributesAsXmlString))

        if (n.isLeaf) {
          sb.append(StringEscapeUtils.escapeXml(n.text))
          sb.append("</%s>".format(n.nodeType))
        } else {
          for (c <- n.content) {
            sb.append("\n")
            visitXml(c, level + 1, indentation, sb)
          }
          sb.append("\n")
          sb.append(indentation)
          sb.append("</%s>".format(n.nodeType))
        }
      }
    }
  }

  def toJson(n: RenderNode, sequences: Seq[List[String]]): String = {
    util.Json.toJson(
      xml = XML.loadString(toXMLString(n)),
      escapeNamespaces = true,
      sequences = sequences
    )
  }

}