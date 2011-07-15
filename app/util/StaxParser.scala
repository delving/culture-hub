package util

import org.codehaus.stax2.XMLInputFactory2
import org.codehaus.stax2.XMLStreamReader2
import javax.xml.stream.events.XMLEvent
import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource
import javax.xml.namespace.QName
import javax.xml.stream.XMLStreamException
import java.io.{IOException, ByteArrayInputStream, InputStream}
import org.scalatest.Stopper

trait StaxParser {

  // STAX set-up
  val xmlif: XMLInputFactory2 = StaxFactory.newInstance()
  xmlif.configureForSpeed()

  @throws(classOf[XMLStreamException])
  @throws(classOf[IOException])
  def parse(inputStream: InputStream)(pf: PartialFunction[Event, Any]) = {
    val source: Source = new StreamSource(inputStream, "UTF-8")
    val input = xmlif.createXMLStreamReader(source).asInstanceOf[XMLStreamReader2]

    var continue = true
    while (continue) {
      input.next()
      val eventType = input.getEventType
      val e: Event = Event(eventType, input)
      try {
        println("parse one")
        pf.apply(e)
        continue = input.hasNext
      } catch {
        case stop: StopException => continue = false
        case other: Throwable => throw other
      }
    }
  }

  class StopException extends Exception

  case class Event(eventType: Int, input: XMLStreamReader2)

}

/*
class MongoObjectParser {
  def this(inputStream: InputStream, recordRoot: Path, uniqueElement: Path, metadataPrefix: String, namespaceUri: String) {
    this ()
    var xmlif: XMLInputFactory2 = XMLInputFactory2.newInstance.asInstanceOf[XMLInputFactory2]
    xmlif.configureForSpeed()
    var source: Source = new StreamSource(inputStream, "UTF-8")
    this.input = xmlif.createXMLStreamReader(source).asInstanceOf[XMLStreamReader2]
    for (ns <- MetadataNamespace.values) {
      this.namespaces.put(ns.getPrefix, ns.getUri)
    }
    this.namespaces.put(metadataPrefix, namespaceUri)
  }

  private var input: XMLStreamReader2 = null
  private var path: Path = new Path
  private var pathWithinRecord: Path = new Path
  private var namespaces: DBObject = mob
  private var hasher: Hasher = new Hasher

  @SuppressWarnings(Array("unchecked")) def nextRecord: MongoObjectParser.Record = {
    var record: MongoObjectParser.Record = null
    var xmlBuffer: StringBuilder = new StringBuilder
    var valueBuffer: StringBuilder = new StringBuilder
    var uniqueBuffer: StringBuilder = null
    var uniqueContent: String = null
    var building: Boolean = true
    while (building) {
      input.getEventType match {
        case XMLEvent.START_DOCUMENT =>
        case XMLEvent.NAMESPACE =>
          System.out.println("namespace: " + input.getName)
        case XMLEvent.START_ELEMENT =>
          path.push(Tag.create(input.getName.getPrefix, input.getName.getLocalPart))
          if (record == null && (path == recordRoot)) {
            record = new MongoObjectParser.Record
          }
          if (record != null) {
            pathWithinRecord.push(path.peek)
            if (valueBuffer.length > 0) {
              throw new IOException("Content and subtags not permitted")
            }
            if (path == uniqueElement) {
              uniqueBuffer = new StringBuilder
            }
            var prefix: String = input.getPrefix
            var uri: String = input.getNamespaceURI
            if (prefix != null && !prefix.isEmpty) {
              namespaces.put(prefix, uri)
            }
            if (!(path == recordRoot)) {
              xmlBuffer.append("<").append(input.getPrefixedName)
              if (input.getAttributeCount > 0) {
                {
                  var walk: Int = 0
                  while (walk < input.getAttributeCount) {
                    {
                      var qName: QName = input.getAttributeName(walk)
                      var attrName: String = qName.getLocalPart
                      if (qName.getPrefix.isEmpty) {
                        var value: String = input.getAttributeValue(walk)
                        xmlBuffer.append(' ').append(attrName).append("=\"").append(value).append("\"")
                      }
                    }
                    ({
                      walk += 1;
                      walk
                    })
                  }
                }
              }
              xmlBuffer.append(">")
            }
          }
        case XMLEvent.CHARACTERS =>
        case XMLEvent.CDATA =>
          if (record != null) {
            var text: String = input.getText
            if (!text.trim.isEmpty) {
              {
                var walk: Int = 0
                while (walk < text.length) {
                  {
                    var c: Char = text.charAt(walk)
                    c match {
                      case '&' =>
                        valueBuffer.append("&amp;")
                        break //todo: break is not supported
                      case '<' =>
                        valueBuffer.append("&lt;")
                        break //todo: break is not supported
                      case '>' =>
                        valueBuffer.append("&gt;")
                        break //todo: break is not supported
                      case '"' =>
                        valueBuffer.append("&quot;")
                        break //todo: break is not supported
                      case '\'' =>
                        valueBuffer.append("&apos;")
                        break //todo: break is not supported
                      case _ =>
                        valueBuffer.append(c)
                    }
                  }
                  ({
                    walk += 1;
                    walk
                  })
                }
              }
              if (uniqueBuffer != null) {
                uniqueBuffer.append(text)
              }
            }
          }
        case XMLEvent.END_ELEMENT =>
          if (record != null) {
            if (path == recordRoot) {
              record.getMob.put(metadataPrefix, xmlBuffer.toString)
              if (uniqueContent != null) {
                record.getMob.put(MetaRepo.Record.UNIQUE, uniqueContent)
              }
              record.getMob.put(MetaRepo.Record.HASH, createHashToPathMap(record.getValueMap))
              xmlBuffer.setLength(0)
              building = false
            }
            else {
              if (valueBuffer.length > 0) {
                if (uniqueBuffer != null) {
                  var unique: String = uniqueBuffer.toString.trim
                  if (!unique.isEmpty) {
                    uniqueContent = unique
                  }
                  uniqueBuffer = null
                }
                var value: String = valueBuffer.toString
                xmlBuffer.append(value)
                record.getValueMap.put(pathWithinRecord.toString, value)
              }
              xmlBuffer.append("</").append(input.getPrefixedName).append(">\n")
              valueBuffer.setLength(0)
            }
            pathWithinRecord.pop
          }
          path.pop
          break //todo: break is not supported
        case XMLEvent.END_DOCUMENT =>
          break //todo: break is not supported
      }
      if (!input.hasNext) {
        break //todo: break is not supported
      }
      input.next
    }
    return record
  }

  private def createHashToPathMap(valueMap: Multimap[String, String]): AnyRef = {
    var mob: DBObject = mob
    for (path <- valueMap.keys) {
      var index: Int = 0
      for (value <- valueMap.get(path)) {
        mob.put(hasher.getHashString(value), if (index == 0) path
        else String.format("%s_%d", path, ({
          index += 1;
          index
        })))
        ({
          index += 1;
          index
        })
      }
    }
    return mob
  }

  def getNamespaces: DBObject = {
    return namespaces
  }

  def close: Unit = {
    try {
      input.close
    }
    catch {
      case e: XMLStreamException => {
        e.printStackTrace
      }
    }
  }


}
*/
