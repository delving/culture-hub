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

import io.Source
import java.io.InputStream
import xml.pull._
import scala.collection.JavaConverters._
import models.DataSet
import xml.{TopScope, NamespaceBinding}
import core.storage.Record
import scala.Predef._

/**
 * Parses an incoming stream of records formatted according to the Delving SIP source format.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class SimpleDataSetParser(is: InputStream, dataSet: DataSet) extends Iterator[Record] {

  private val ns = collection.mutable.Map.empty[String, String]
  private val parser = new XMLEventReader(Source.fromInputStream(is))
  private var recordCounter: Int = 0
  private var isDone = false


  {
    parser.next() match {
      case EvElemStart(_, "delving-sip-source", _, scope) =>
        extractNamespaces(scope, ns)
        DataSet.updateNamespaces(dataSet.spec, ns.toMap)
      case _ => throw new IllegalArgumentException("Source input does not start with <delving-sip-source>")
    }
  }

  def namespaces = ns.toMap

  def hasNext: Boolean = !isDone && parser.hasNext

  // there's a salat bug that leads to our Map[String, List[Int]] not being deserialized properly, so we do it here
  val invalidRecords = dataSet.invalidRecords.map(valid => {
    val key = valid._1.toString
    val value: Set[Int] = valid._2.asInstanceOf[com.mongodb.BasicDBList].asScala.map(index => index match {
      case int if int.isInstanceOf[Int] => int.asInstanceOf[Int]
      case double if double.isInstanceOf[java.lang.Double] => double.asInstanceOf[java.lang.Double].intValue()
    }).toSet
    (key, value)
  }).toMap[String, Set[Int]]

  def next(): Record = {
    if(isDone) throw new java.util.NoSuchElementException("next on empty iterator")

    var hasParsedOne = false
    var inRecord = false
    var elementHasContent = false

    // the whole content of one record
    val recordXml = new StringBuilder()

    // the value of one field
    val fieldValueXml = new StringBuilder()

    var record: Record = null
    var recordId: String = null

    while (!hasParsedOne || !isDone) {
      val next = parser.next()
      next match {
        case EvElemStart(pre, "input", attrs, _) =>
          inRecord = true
          val mayId = attrs.get("id").headOption
          if (mayId != None) recordId = mayId.get.text
        case EvElemEnd(_, "input") =>
          inRecord = false
          record = Record(
            id = recordId,
            document = """<input id="%s">%s</input>""".format(recordId, recordXml.toString()),
            schemaPrefix = "raw",
            invalidTargetSchemas = getInvalidMappings(dataSet, recordCounter)
          )
          recordXml.clear()
          recordId = null
          recordCounter += 1
          hasParsedOne = true
        case elemStart@EvElemStart(prefix, label, attrs, scope) if (inRecord) =>
          recordXml.append(elemStartToString(elemStart))
          elementHasContent = false
        case EvText(text) if (inRecord) =>
          if (text != null && text.size > 0) elementHasContent = true
          recordXml.append(text)
          fieldValueXml.append(text)
        case EvEntityRef(text) if (inRecord) =>
          elementHasContent = true
          recordXml.append("&%s;".format(text))
          fieldValueXml.append(text)
        case elemEnd@EvElemEnd(_, _) if (inRecord) =>
          if (!elementHasContent) {
            val rollback = recordXml.substring(0, recordXml.length - ">".length())
            recordXml.clear()
            recordXml.append(rollback).append("/>")
          } else {
            recordXml.append(elemEndToString(elemEnd))
          }
          fieldValueXml.clear()
        case EvElemEnd(_, "delving-sip-source") =>
          isDone = true
        case some@_ =>
      }
    }
    if (!parser.hasNext) isDone = true
    record
  }

  private def getInvalidMappings(dataSet: DataSet, index: Int): List[String] = invalidRecords.flatMap(invalid => if (invalid._2.contains(index)) Some(invalid._1) else None).toList


  private def elemStartToString(start: EvElemStart): String = {
    val attrs = scala.xml.Utility.sort(start.attrs).toString().trim()
    if (attrs.isEmpty)
      "<%s%s>".format(prefix(start.pre), start.label)
    else
      "<%s%s %s>".format(prefix(start.pre), start.label, attrs)
  }

  private def elemEndToString(end: EvElemEnd): String = "</%s%s>".format(prefix(end.pre), end.label)

  private def prefix(pre: String): String = if (pre != null) pre + ":" else ""

  private def extractNamespaces(ns: NamespaceBinding, namespaces: collection.mutable.Map[String, String]) {
    if (ns == TopScope) return
    if (ns.prefix != null) namespaces.put(ns.prefix, ns.uri)
    extractNamespaces(ns.parent, namespaces)
  }

}

