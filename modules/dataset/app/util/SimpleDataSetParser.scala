/*
 * Copyright 2011 Delving B.V.
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

package util

import java.io.InputStream
import models.DataSet
import core.storage.Record
import scales.utils._
import ScalesUtils._
import scales.xml._
import ScalesXml._
import org.apache.commons.lang.StringEscapeUtils
import javax.xml.stream.XMLInputFactory
import org.codehaus.stax2.XMLInputFactory2

/**
 * Parses an incoming stream of records formatted according to the Delving SIP source format.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class SimpleDataSetParser(is: InputStream, dataSet: DataSet) extends Iterator[Record] {

  private val ns = collection.mutable.Map.empty[String, String]
  private val pull = pullXml(is, parserFactoryPool = CustomStaxInputFactoryPool)
  private var recordCounter: Int = 0
  private var isDone = false
  private var lookAhead: Record = null

  {
    // skip start of document
    pull.next() match {
      case Left(Elem(qname, _, namespaces)) if qname.local == "delving-sip-source" =>
        ns ++= namespaces
        DataSet.dao(dataSet.orgId).updateNamespaces(dataSet.spec, ns.toMap)
      case _ => throw new IllegalArgumentException("Source input does not start with <delving-sip-source>")
    }
    parseNext() match {
      case Some(l) => lookAhead = l
      case None => isDone = true
    }
  }

  def namespaces = ns.toMap

  def hasNext: Boolean = !isDone

  def next(): Record = {
    if (isDone) throw new java.util.NoSuchElementException("next on empty iterator")
    val l = lookAhead
    parseNext() match {
      case Some(ahead) => lookAhead = ahead
      case None => isDone = true
    }
    l
  }

  private def parseNext(): Option[Record] = {

    var hasParsedOne = false
    var inRecord = false
    var elementHasContent = false

    // the whole content of one record
    val recordXml = new StringBuilder()

    // the value of one field
    val fieldValueXml = new StringBuilder()

    var record: Record = null
    var recordId: String = null

    while (!hasParsedOne) {
      if (!pull.hasNext) return None
      val next = pull.next()
      next match {
        case Left(Elem(qname, attrs, namespaces)) if qname.local == "input" =>
          inRecord = true
          val mayId = attrs.find(_.name.local == "id")
          if (mayId != None) recordId = StringEscapeUtils.escapeXml(mayId.get.value)
        case Right(EndElem(name, _)) if (name.local == "input") =>
          inRecord = false
          record = Record(
            id = recordId,
            document = """<input id="%s">%s</input>""".format(recordId, recordXml.mkString),
            schemaPrefix = "raw"
          )
          recordXml.clear()
          recordId = null
          recordCounter += 1
          hasParsedOne = true
        case elemStart @ Left(Elem(qname, attrs, ns)) if (inRecord) =>
          recordXml.append(elemStartToString(qname, attrs, ns))
          elementHasContent = false
        case Left(Text(txt)) if (inRecord) =>
          if (txt != null && txt.size > 0) elementHasContent = true
          val encoded = StringEscapeUtils.escapeXml(txt)
          recordXml.append(encoded)
          fieldValueXml.append(encoded)
        case Left(CData(data)) if (inRecord) =>
          if (data != null && data.size > 0) elementHasContent = true
          val d = """<![CDATA[%s]]>""".format(data)
          recordXml.append(d)
          fieldValueXml.append(d)
        case elemEnd @ Right(EndElem(qname, _)) if (inRecord) =>
          if (!elementHasContent) {
            val rollback = recordXml.substring(0, recordXml.length - ">".length())
            recordXml.clear()
            recordXml.append(rollback).append("/>")
          } else {
            recordXml.append(elemEndToString(qname))
          }
          fieldValueXml.clear()
        case some @ _ =>
      }
    }
    Some(record)
  }

  private def elemStartToString(qname: QName, attributes: ListSet[Attribute], ns: Map[String, String]): String = {
    val attrs = attributes.
      toList.
      filterNot(a => a.prefix.isEmpty && (a.local == null || a.local.trim.length == 0)).
      sortBy(a => a.name.local).
      map(a => a.prefix.getOrElse("") + (if (a.prefix.isDefined && a.local != null && a.local.trim.length > 0) ":" else "") + a.local + "=\"" + StringEscapeUtils.escapeXml(a.value) + "\"")
    if (attrs.isEmpty)
      "<%s%s>".format(prefix(qname.prefix), qname.local)
    else
      "<%s%s %s>".format(prefix(qname.prefix), qname.local, attrs.mkString(" "))
  }

  private def elemEndToString(qname: QName): String = "</%s%s>".format(prefix(qname.prefix), qname.local)

  private def prefix(pre: Option[String]): String = if (pre.isDefined) pre.get + ":" else ""
}

object CustomStaxInputFactoryPool extends scales.utils.SimpleUnboundedPool[XMLInputFactory] {
  pool =>

  val cdata = "http://java.sun.com/xml/stream/properties/report-cdata-event"

  def create = {
    val xmlif = XMLInputFactory.newInstance().asInstanceOf[XMLInputFactory2]
    if (xmlif.isPropertySupported(cdata)) {
      xmlif.setProperty(cdata, java.lang.Boolean.TRUE)
    }
    if (xmlif.isPropertySupported(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES)) {
      xmlif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, java.lang.Boolean.FALSE)
    }
    if (xmlif.isPropertySupported(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES)) {
      xmlif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, java.lang.Boolean.FALSE)
    }
    if (xmlif.isPropertySupported(XMLInputFactory.IS_COALESCING)) {
      xmlif.setProperty(XMLInputFactory.IS_COALESCING, java.lang.Boolean.FALSE)
    }
    xmlif.configureForSpeed()

    xmlif

  }
}