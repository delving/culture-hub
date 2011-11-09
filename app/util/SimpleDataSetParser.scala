package util

import io.Source
import java.io.{ByteArrayInputStream, InputStream}
import xml.pull._
import collection.mutable.{MultiMap, HashMap}
import xml.{TopScope, NamespaceBinding}
import eu.delving.metadata.{Hasher, Tag, Path}
import models.{MetadataRecord, DataSet}
import org.apache.commons.lang.StringEscapeUtils

/**
 * Parses an incoming stream of records formatted according to the Delving SIP source format.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class SimpleDataSetParser(is: InputStream, dataSet: DataSet) {

  val parser = new XMLEventReader(Source.fromInputStream(is))
  val hasher = new Hasher

  def nextRecord: Option[MetadataRecord] = {

    var hasParsedOne = false
    var recordCounter = 0
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
      if (!parser.hasNext()) return None
      val next = parser.next()
      next match {
        case EvElemStart(_, "delving-sip-source", _, scope) =>
          val namespaces = collection.mutable.Map.empty[String, String]
          extractNamespaces(scope, namespaces)
          val updatedDataSet = dataSet.copy(namespaces = namespaces.toMap)
          DataSet.save(updatedDataSet)
        case EvElemStart(pre, "input", attrs, _) =>
          inRecord = true
          val mayId = attrs.get("id").headOption
          if(mayId != None) recordId = mayId.get.text
        case EvElemEnd(_, "input") =>
          inRecord = false
          record = MetadataRecord(
            rawMetadata = Map("raw" -> recordXml.toString()),
            validOutputFormats = getValidMappings(dataSet, recordCounter),
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

  private def getValidMappings(dataSet: DataSet, index: Int): List[String] = {
    val invalidRecords: Map[String, _] = dataSet.invalidRecords
    val mappings: Iterable[String] = for (valid <- invalidRecords) yield {
      val thing = valid._2

      // workaround for a Salat bug
      if(thing.isInstanceOf[com.mongodb.BasicDBList]) {
        if(thing.asInstanceOf[com.mongodb.BasicDBList].contains(index)) "" else valid._1
      } else if(thing.asInstanceOf[List[Int]].contains(index)) "" else valid._1
    }
    mappings.filterNot(_.length == 0).toList
  }

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

object SimpleDataSetParser {

  def main(args: Array[String]) {

    import models.salatContext._

    val ds = DataSet.findBySpec("Verzetsmuseum").get

    val txt =
    """
    <?xml version='1.0' encoding='UTF-8'?>
<delving-sip-source xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<input id="1">
<edit.source>collect>intern</edit.source>
<edit.source>collect>intern</edit.source>
<edit.source>collect>intern</edit.source>
<edit.source>collect>intern</edit.source>
<edit.time>16:14:27</edit.time>
<edit.time>15:45:34</edit.time>
<edit.time>11:55:25</edit.time>
<edit.time>16:16:41</edit.time>
<edit.name>d.terpstra</edit.name>
<edit.name>d.terpstra</edit.name>
<edit.name>alina</edit.name>
<edit.name>alina</edit.name>
<input.name>Conversie AIS</input.name>
<association.subject>Heerenveen</association.subject>
<association.subject>Hemelum</association.subject>
<association.subject.type option="GEOKEYW" value="GEOKEYW"><text language="0">geography</text>
<text language="1">geografie</text>
<text language="2">g<C3><A9>ographie</text>
<text language="3">Geografie</text>
<text language="4"><D8><AC><D8><BA><D8><B1><D8><A7><D9><81><D9><8A></text>
<text language="6"><CE><B3><CE><B5><CF><89><CE><B3><CF><81><CE><B1><CF><86><CE><AF><CE><B1></text>
</association.subject.type>
<association.subject.type option="GEOKEYW" value="GEOKEYW"><text language="0">geography</text>
<text language="1">geografie</text>
<text language="2">g<C3><A9>ographie</text>
<text language="3">Geografie</text>
<text language="4"><D8><AC><D8><BA><D8><B1><D8><A7><D9><81><D9><8A></text>
<text language="6"><CE><B3><CE><B5><CF><89><CE><B3><CF><81><CE><B1><CF><86><CE><AF><CE><B1></text>
</association.subject.type>
<edit.date>2009-04-15</edit.date>
<edit.date>2009-04-15</edit.date>
<edit.date>2007-11-19</edit.date>
<edit.date>2007-11-07</edit.date>
<input.date>2005-02-08</input.date>
<acquisition.method>schenking</acquisition.method>
<production.place>Friesland</production.place>
<acquisition.source>Yntema</acquisition.source>
<free_field.content>ja</free_field.content>
<free_field.type>Ge<C3><AF>llustreerd</free_field.type>
<acquisition.date>1979</acquisition.date>
<valuation.value.currency>EUR</valuation.value.currency>
<condition>matig</condition>
<title>Archiefmap van Iede Boukes Yntema, slachtoffer verzet</title>
<valuation.date>2004</valuation.date>
<valuation.value>100.00</valuation.value>
<location>ARCH</location>
<association.period.date.start>1945-03-17</association.period.date.start>
<related_object.notes>VM000008 VM000009</related_object.notes>
<number_of_parts>1</number_of_parts>
<object_name>map</object_name>
<location.default>ARCH</location.default>
<object_number>00001</object_number>
<institution.code>1023</institution.code>
<content.motif.specific>PERSONEN/YNTEMA, IEDE BOUKES SLACHTOFFER</content.motif.specific>
<content.motif.general>verzet</content.motif.general>
<production.date.notes>1902 Hemelum</production.date.notes>
<dimension.notes>-</dimension.notes>
<institution.place/>
<description>Slachtoffer verzet</description>
<institution.name>Verzetsmuseum Friesland</institution.name>
<administration_name>Objecten</administration_name>
<percentH>J</percentH>
</input>
</delving-sip-source>"""

    val bis = new ByteArrayInputStream(txt.getBytes)

    val parser = new SimpleDataSetParser(bis, ds)

    println(parser.nextRecord)
    println(parser.nextRecord)
    println(parser.nextRecord)

  }

}