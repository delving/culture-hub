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
import collection.mutable.{MultiMap, HashMap}
import xml.{TopScope, NamespaceBinding}
import eu.delving.metadata.{Hasher, Tag, Path}
import models.{MetadataRecord, DataSet}
import scala.collection.JavaConverters._

/**
 * Parses an incoming stream of records formatted according to the Delving SIP source format.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class SimpleDataSetParser(is: InputStream, dataSet: DataSet) {

  val parser = new XMLEventReader(Source.fromInputStream(is))
  val hasher = new Hasher
  var recordCounter = 0

  // there's a salat bug that leads to our Map[String, List[Int]] not being deserialized properly, so we do it here
  val invalidRecords = dataSet.invalidRecords.map(valid => {
    val key = valid._1.toString
    val value: Set[Int] = valid._2.asInstanceOf[com.mongodb.BasicDBList].asScala.map(index => index match {
      case int if int.isInstanceOf[Int] => int.asInstanceOf[Int]
      case double if double.isInstanceOf[java.lang.Double] => double.asInstanceOf[java.lang.Double].intValue()
    }).toSet
    (key, value)
  }).toMap[String, Set[Int]]

  def nextRecord: Option[MetadataRecord] = {

    var hasParsedOne = false
    var inRecord = false
    var inIdentifierElement = false
    var justLeftIdentifierElement = false
    var elementHasContent = false
    val valueMap = new HashMap[String, collection.mutable.Set[String]]() with MultiMap[String, String]
    val path = new Path()

    // the whole content of one record
    val recordXml = new StringBuilder()

    // the value of one field
    val fieldValueXml = new StringBuilder()

    var record: MetadataRecord = null
    var recordId: String = null

    while (!hasParsedOne) {
      if (!parser.hasNext) return None
      val next = parser.next()
      next match {
        case EvElemStart(_, "delving-sip-source", _, scope) =>
          val namespaces = collection.mutable.Map.empty[String, String]
          extractNamespaces(scope, namespaces)
          DataSet.updateNamespaces(dataSet.spec, namespaces.toMap)
        case EvElemStart(pre, "input", attrs, _) =>
          inRecord = true
          val mayId = attrs.get("id").headOption
          if(mayId != None) recordId = mayId.get.text
        case EvElemEnd(_, "input") =>
          inRecord = false
          record = MetadataRecord(
            hubId = "%s_%s_%s".format(dataSet.orgId, dataSet.spec, recordId),
            rawMetadata = Map("raw" -> recordXml.toString()),
            validOutputFormats = getValidMappings(dataSet, recordCounter),
            transferIdx = Some(recordCounter),
            localRecordKey = recordId,
            globalHash = hasher.getHashString(recordXml.toString()),
            hash = createHashToPathMap(valueMap))
          recordXml.clear()
          recordId = null
          recordCounter += 1
          hasParsedOne = true
        case EvElemStart(prefix, "_id", attrs, scope) if(inRecord) =>
          inIdentifierElement = true
        case EvElemEnd(_, "_id") if(inRecord) =>
          inIdentifierElement = false
          justLeftIdentifierElement = true
        case elemStart@EvElemStart(prefix, label, attrs, scope) if (inRecord) =>
          path.push(Tag.element(prefix, label))
          recordXml.append(elemStartToString(elemStart))
          elementHasContent = false;
        case EvText(text) if(inRecord && inIdentifierElement) =>
          recordId = text
        case EvText(text) if(inRecord && !inIdentifierElement && recordId != null && !justLeftIdentifierElement) =>
          if(text != null && text.size > 0) elementHasContent = true
          recordXml.append(text)
          fieldValueXml.append(text)
        case EvEntityRef(text) if(inRecord && !inIdentifierElement && recordId != null && !justLeftIdentifierElement) =>
          elementHasContent = true
          recordXml.append("&%s;".format(text))
          fieldValueXml.append(text)
        case EvText(text) if(inRecord && !inIdentifierElement && recordId != null && justLeftIdentifierElement) =>
          justLeftIdentifierElement = false
        case elemEnd@EvElemEnd(_, _) if(inRecord) =>
          valueMap.addBinding(path.toString, fieldValueXml.toString())
          if(!elementHasContent) {
            val rollback = recordXml.substring(0, recordXml.length - ">".length())
            recordXml.clear()
            recordXml.append(rollback).append("/>")
          } else {
            recordXml.append(elemEndToString(elemEnd))
          }
          path.pop()
          fieldValueXml.clear()
        case some@_ =>
      }
    }
    Option(record)
  }

  private def getValidMappings(dataSet: DataSet, index: Int): List[String] = invalidRecords.flatMap(valid => if(valid._2.contains(index)) None else Some(valid._1)).toList


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
    if(ns.prefix != null) namespaces.put(ns.prefix, ns.uri)
    extractNamespaces(ns.parent, namespaces)
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