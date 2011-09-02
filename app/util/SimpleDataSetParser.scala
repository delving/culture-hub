package util

import io.Source
import java.io.{ByteArrayInputStream, InputStream}
import xml.pull._
import collection.mutable.{MultiMap, HashMap}
import xml.{TopScope, NamespaceBinding}
import eu.delving.metadata.{Hasher, Tag, Path}
import models.{MetadataRecord, DataSet}
import org.scala_tools.time.Imports._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class SimpleDataSetParser(is: InputStream, dataSet: DataSet) {

  val parser = new XMLEventReader(Source.fromInputStream(is))
  val hasher = new Hasher

  def nextRecord: Option[MetadataRecord] = {

    var hasParsedOne = false
    var inRecord = false
    val valueMap = new HashMap[String, collection.mutable.Set[String]]() with MultiMap[String, String]
    val path = new Path()

    // the whole content of one record
    val recordXml = new StringBuilder()

    // the value of one field
    val fieldValueXml = new StringBuilder()

    var record: MetadataRecord = null

    while (!hasParsedOne) {
      if(!parser.hasNext()) return None
      parser.next() match {
        case EvElemStart(_, "delving-sip-source", _, scope) =>
          val namespaces = collection.mutable.Map.empty[String, String]
          extractNamespaces(scope, namespaces)
          val updatedDataSet = dataSet.copy(namespaces = namespaces.toMap)
          DataSet.save(updatedDataSet)
        case EvElemStart(pre, "input", _, _) =>
          inRecord = true
        case EvElemEnd(_, "input") =>
          inRecord = false
          record = MetadataRecord(
            rawMetadata = Map("raw" -> recordXml.toString()),
            localRecordKey = "",
            globalHash = hasher.getHashString(recordXml.toString()),
            hash = createHashToPathMap(valueMap))
          recordXml.clear()
          hasParsedOne = true
        case elemStart@EvElemStart(prefix, label, attrs, scope) =>
          if (inRecord) {
            path.push(Tag.create(prefix, label))
            recordXml.append(elemStartToString(elemStart))
          }
        case EvText(text) =>
          if (inRecord) {
            recordXml.append(text)
            fieldValueXml.append(text)
          }
        case elemEnd@EvElemEnd(_, _) =>
          if (inRecord) {
            valueMap.addBinding(path.toString, fieldValueXml.toString())
            recordXml.append(elemEndToString(elemEnd))
            path.pop()
            fieldValueXml.clear()
          }
        case _ =>
      }
    }
    Option(record)

  }

  private def elemStartToString(start: EvElemStart): String = {
    val builder = new StringBuilder()
    builder.append("<").append(prefix(start.pre)).append(start.label).append(start.attrs.toString()).append(">")
    builder.toString()
  }

  private def elemEndToString(end: EvElemEnd): String = {
    val builder = new StringBuilder
    builder.append("</").append(prefix(end.pre)).append(end.label).append(">")
    builder.toString()
  }

  private def prefix(pre: String): String = if (pre != null) pre + ":" else ""

  private def extractNamespaces(ns: NamespaceBinding, namespaces: collection.mutable.Map[String, String]) {
    if (ns == TopScope) return
    namespaces.put(ns.prefix, ns.uri)
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

object SimpleDataSetParser {

  def main(args: Array[String]) {

    import models.salatContext._

    initSalat()

    val ds = DataSet.findBySpec("Verzetsmuseum").get

    val txt =
      """<delving-sip-source xmlns:foo="http://www.foo.com" xmlns:bar="http://www.bar.com">
           <input>
             <a asd="bef" asa="asa">a1</a>
             <b>b1</b>
           </input>
           <input>
             <a>a2</a>
             <b>b2</b>
           </input>
         </delving-sip-source>
      """

    val bis = new ByteArrayInputStream(txt.getBytes)

    val parser = new SimpleDataSetParser(bis, ds)

    println(parser.nextRecord)
    println(parser.nextRecord)
    println(parser.nextRecord)

  }

}