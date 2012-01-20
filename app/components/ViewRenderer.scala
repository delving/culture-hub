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
import scala.collection.JavaConverters._
import groovy.util.{Node, XmlParser}
import xml.XML
import collection.mutable.{HashMap, ArrayBuffer}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ViewRenderer {

  def renderView(recordDefinition: File, view: String, record: Node): List[Node] = {

    def throwUnknownElement(e: String) {
      throw new RuntimeException("Unknown element '%s'".format(e))
    }

    val result = RenderNode("root")
    
    var current: RenderNode = result
    
    def enter(node: scala.xml.Node, nodeType: String, attr: (String, Any)*)(block: scala.xml.Node => Unit) {
      val entered = RenderNode(nodeType)
      attr foreach { entered addAttr _ }
      val previous = current
      current += entered
      current = entered
      node foreach { block }
      current = previous
    }

    def append(nodeType: String, attr: (String, Any)*) {
      val newNode = RenderNode(nodeType)
      attr foreach { newNode addAttr _}
      current += newNode
    }

    val xml = XML.loadFile(recordDefinition)
    (xml \ "views" \ "view").filter(_ \ "@id" == view).headOption match {
      case Some(viewDefinition) =>

        viewDefinition foreach {
          r => r.label match {
            case "row" =>
              enter(r, "row") { c =>
                  c.label match {
                    case "column" =>
                      enter(c, "column", ("id" -> (c \ "@id").text)) { e => e.label match {
                        case "field" =>
                          // initialize field and its meta-data
                          append("field", ("label", (c \ "@label").text), ("label", (c \ "@queryLink").text.toBoolean))

                          // fetch the field data based on the path(s)
                          val data = fetchPaths(record, (c \ "@path").text.split(",").map(_.trim).toList)
                          println(data)


                        case "list" =>
                        case u => throwUnknownElement(u)
                      }
                      
                      }
                      
  
                    case u => throwUnknownElement(u)
                  }
                
              }
            case u => throwUnknownElement(u)

          }
        }

      case None => throw new RuntimeException("Could not find view definition '%s' in file '%s'".format(view, recordDefinition.getAbsolutePath))
    }



    List()

  }

  private def fetchPaths(rootNode: Node, paths: Seq[String]): Seq[String] = {
    (for(path <- paths) yield {
      // basic traversal assuming we only have single elements, or something like that
      fetch(rootNode, path.split("/"), 0)
    }).flatten
  }
  
  private def fetch(n: Node, p: Array[String], level: Int): Option[String] = {
    import scala.collection.JavaConversions._
    val t = n.children().map(_.asInstanceOf[Node]).find(_.name() == p(level)).getOrElse(return None)
    if(level == p.length) Some(t.text()) else fetch(t, p, level + 1)
  }

  private def testData(): Node = {

    // test record, hierarchical
    val testRecord =
      <record>
        <delving:summaryFields>
          <delving:title>A test hierarchical record</delving:title>
          <delving:description>This is a test record</delving:description>
          <delving:creator>John Lennon</delving:creator>
          <delving:owner>Museum of Music</delving:owner>
        </delving:summaryFields>
        <dc:data>
          <dc:type>picture</dc:type>
        </dc:data>
        <icn:data>
          <icn:general>
            <icn:material>Wood</icn:material>
            <icn:technique>Carving</icn:technique>
          </icn:general>
        </icn:data>
      </record>

    new XmlParser().parseText(testRecord.toString())
  }
  
  
  def main(args: String*) {
    renderView(play.Play.getFile("conf/icn-record-definition.xml").getAbsoluteFile, "full", testData())
  }
  

}

class RenderNode(nodeType: String) {
  
  val contentBuffer = new ArrayBuffer[RenderNode]
  
  val attributes = new HashMap[String, AnyRef]
  
  def content: List[RenderNode] = contentBuffer.toList
  
  def += (node: RenderNode) {
    contentBuffer += node
  }
  
  def attr(key: String) = attributes(key)
  def addAttr(key: String, value: AnyRef) = attributes + (key -> value)
  def addAttr(element: (String, Any)) = attributes + element
  
  
  
}

object RenderNode {
  
  def apply(nodeType: String) = new RenderNode(nodeType)

}


