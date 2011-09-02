package util

import eu.delving.metadata.{Path, Hasher, Facts}
import models.{MetadataRecord, RecordDefinition}
import java.io.{IOException, InputStream}
import javax.xml.stream.XMLStreamConstants._
import eu.delving.metadata.Tag
import scala.collection.mutable.HashMap
import scala.collection.mutable.MultiMap
import org.scala_tools.time.Imports._


/**
 * TODO rewrite this into something that looks like scala code
 * TODO switch to the native scala pull parser when we use a release that has a fixed version of it
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSetParser(inputStream: InputStream, namespaces: Map[String, String], mdFormat: RecordDefinition, metadataPrefix: String, facts: Facts) extends StaxParser {

  private val hasher: Hasher = new Hasher
  private val input = createReader(inputStream)
  private val allNamespaces: scala.collection.mutable.Map[String, String] = scala.collection.mutable.Map.empty[String, String] ++ namespaces
  private val path: Path = new Path
  private val pathWithinRecord: Path = new Path
  private val recordRoot: Path = new Path(facts.getRecordRootPath)
  private val uniqueElement: Path = new Path(facts.getUniqueElementPath)

  def nextRecord(): Option[MetadataRecord] = {

    var record: Option[MetadataRecord] = None
    val valueMap = new HashMap[String, collection.mutable.Set[String]]() with MultiMap[String, String]

    val xmlBuffer: StringBuilder = new StringBuilder
    val valueBuffer: StringBuilder = new StringBuilder
    var uniqueBuffer: StringBuilder = null
    var uniqueContent: String = null

    var building = true

    while (building) {
      input.getEventType match {
        case START_DOCUMENT =>
        case NAMESPACE => System.out.println("namespace: " + input.getName)
        case START_ELEMENT =>
          path.push(Tag.create(input.getName.getPrefix, input.getName.getLocalPart))
          if (record == None && (path == recordRoot)) {
            import eu.delving.sip.IndexDocument
            record = Some(new MetadataRecord(null, Map.empty[String, String], Map.empty[String, IndexDocument], DateTime.now, false, "", "", Map.empty[String, String]))
          }
          if (record != None) {
            pathWithinRecord.push(path.peek)
            if (valueBuffer.length > 0) {
              throw new IOException("Content and subtags not permitted")
            }
            if (path == uniqueElement) uniqueBuffer = new StringBuilder

            // collect namespaces, this can probably be removed
            val prefix: String = input.getPrefix
            if (prefix != null && !input.getPrefix.isEmpty) {
              allNamespaces.put(input.getPrefix, input.getNamespaceURI)
            }
            if (path != recordRoot) {
              xmlBuffer.append("<").append(input.getPrefixedName)
              if (input.getAttributeCount > 0) {
                var walk: Int = 0
                while (walk < input.getAttributeCount) {
                  val qName = input.getAttributeName(walk)
                  val attrName = qName.getLocalPart
                  if (qName.getPrefix.isEmpty) {
                    val value = input.getAttributeValue(walk)
                    xmlBuffer.append(' ').append(attrName).append("=\"").append(value).append("\"")
                  }
                  walk += 1
                }
              }
              xmlBuffer.append(">")
            }
          }
        case CDATA | CHARACTERS =>
          if (record != None) {
            val text = input.getText()
            if (!text.trim.isEmpty) {
              var walk: Int = 0
              while (walk < text.length) {
                valueBuffer.append(escape(text, walk))
                walk += 1
              }
              if (uniqueBuffer != null) uniqueBuffer.append(text)
            }
          }
        case END_ELEMENT =>
          if (record != None) {
            if (path == recordRoot) {
              record = Some(record.get.copy(rawMetadata = record.get.rawMetadata.updated(metadataPrefix, xmlBuffer.toString())))
              if (uniqueContent != null) record = Some(record.get.copy(localRecordKey = uniqueContent))
              record = Some(record.get.copy(hash = createHashToPathMap(valueMap), globalHash = hasher.getHashString(xmlBuffer.toString())))
              xmlBuffer.setLength(0)
              building = false
            } else {
              if (valueBuffer.length > 0) {
                if (uniqueBuffer != null) {
                  val unique: String = uniqueBuffer.toString().trim
                  if (!unique.isEmpty) uniqueContent = unique
                  uniqueBuffer = null
                }
                val value: String = valueBuffer.toString()
                xmlBuffer.append(value)
                valueMap.addBinding(pathWithinRecord.toString, value)
              }
              xmlBuffer.append("</").append(input.getPrefixedName).append(">\n")
              valueBuffer.setLength(0)
            }
            pathWithinRecord.pop()
          }
          path.pop()
        case END_DOCUMENT =>
      }
      if (input.hasNext) {
        input.next()
      } else {
        building = false
      }
    }
    record
  }

  def escape(text: String, walk: Int): String = {
    text.charAt(walk) match {
      case '&' => "&amp;"
      case '<' => "&lt;"
      case '>' => "&gt;"
      case '"' => "&quot;"
      case '\'' => "&apos;"
      case c@_ => c.toString
    }
  }

  private def createHashToPathMap(valueMap: MultiMap[String, String]): Map[String, String] = {
    val bits: Iterable[collection.mutable.Set[(String, String)]] = for (path <- valueMap.keys) yield {
      var index: Int = 0
      val innerBits: collection.mutable.Set[(String, String)] = for (value <- valueMap.get(path).get) yield {
        val foo: String = if (index == 0) path else "%s_%d".format(path, index)
        index += 1
        (hasher.getHashString(value), foo)
      }
      innerBits
    }
    bits.flatten.toMap
  }

}