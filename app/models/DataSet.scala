package models

import java.util.Date
import cake.metaRepo.PmhVerbType.PmhVerb
import org.bson.types.ObjectId
import models.salatContext._
import com.novus.salat.dao.SalatDAO
import controllers.SolrServer
import eu.delving.metadata.{Path, RecordMapping}
import eu.delving.sip.DataSetState

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/8/11 8:12 AM  
 */

case class DataSet(_id: ObjectId = new ObjectId,
                          spec: String,
                          state: DataSetState, // imported from sip-core
                          details: Details,
                          facts_hash: String,
                          source_hash: String = "",
                          downloaded_source_hash: Option[String] = Some(""),
                          namespaces: Option[Map[String, String]] = Some(Map[String, String]()),
                          mappings: Option[Map[String, Mapping]] = Some(Map[String, Mapping]())
                          ) {
  import xml.Elem

  def getHashes : List[String] = {
    val mappingList = mappings.get.values.map(_.mapping_hash).toList
    val hashes: List[String] = facts_hash :: source_hash :: mappingList
    hashes.filterNot(_.isEmpty)
  }

  def hasHash(hash: String): Boolean = getHashes.contains(hash)

  def toXml: Elem = {
    <dataset>
      <spec>{spec}</spec>
      <name>{details.name}</name>
      <state>{state.toString}</state>
      <recordCount>{details.total_records}</recordCount>
      <uploadedRecordCount>{details.uploaded_records}</uploadedRecordCount>
      <recordsIndexed deprecated="This item will be removed later. See mappings">0</recordsIndexed>
      <hashes>
        {getHashes.map(hash => <string>{hash}</string>)}
      </hashes>
      <errorMessage>{details.errorMessage}</errorMessage>
      <mappings>
         {mappings.get.values.map{mapping => mapping.toXml}}
      </mappings>
    </dataset>
  }

  def hasDetails: Boolean = details != null

  def setMapping(mapping: RecordMapping, hash: String, accessKeyRequired: Boolean = true) : DataSet = {
    import eu.delving.metadata.MetadataNamespace
    import cake.metaRepo.MetaRepoSystemException

    val ns: Option[MetadataNamespace] = MetadataNamespace.values().filter(ns => ns.getPrefix == mapping.getPrefix).headOption
    if (ns == None) {
      throw new MetaRepoSystemException(String.format("Namespace prefix %s not recognized", mapping.getPrefix))
    };
    val newMapping = Mapping(recordMapping = mapping,
      format = MetadataFormat(ns.get.getPrefix, ns.get.getSchema, ns.get.getUri, accessKeyRequired),
      mapping_hash = hash)
    // remove First Harvest Step
    this.copy(mappings = Some(this.mappings.get.updated(mapping.getPrefix, newMapping)))
  }

}

object DataSet extends SalatDAO[DataSet, ObjectId](collection = dataSetsCollection) with SolrServer {

  import cake.metaRepo.DataSetNotFoundException
  import com.mongodb.casbah.commons.MongoDBObject
  import java.io.InputStream
  import eu.delving.metadata.Facts

  def getWithSpec(spec: String): DataSet = find(spec).getOrElse(throw new DataSetNotFoundException(String.format("String %s does not exist", spec)))



  def findAll = {
    find(MongoDBObject()).sort(MongoDBObject("name" -> 1)).toList
  }

  def find(spec: String): Option[DataSet] = {
    findOne(MongoDBObject("spec" -> spec))
  }

  def deleteFromSolr(dataSet: DataSet) {
    import org.apache.solr.client.solrj.response.UpdateResponse
    val deleteResponse: UpdateResponse = getSolrServer.deleteByQuery("europeana_collectionName:" + dataSet.spec)
    deleteResponse.getStatus
    getSolrServer.commit
  }

  def parseRecords(inputStream: InputStream, dataSet: DataSet) {
    // todo Manuel
    import com.mongodb.casbah.MongoCollection
    HarvestStep.removeFirstHarvestSteps(dataSet.spec)
    save(dataSet.copy(source_hash = ""))
    val records: MongoCollection = connection("Records." + dataSet.spec)
    records.drop()
    try {
      import java.io.ByteArrayInputStream
      import eu.delving.metadata.{Path, Facts}
      val details: Details = dataSet.details
      val facts: Facts = Facts.read(new ByteArrayInputStream(details.facts_bytes))
//      val parser: MongoObjectParser = new MongoObjectParser(inputStream, new Path(facts.getRecordRootPath), new Path(facts.getUniqueElementPath), details.getMetadataFormat.getPrefix, details.getMetadataFormat.getNamespace)
//      var record: MongoObjectParser.Record = null
//      var modified: Date = new Date
//      `object`.put(NAMESPACES, parser.getNamespaces)
//      var recordCount: Int = 0
//      while ((({
//        record = parser.nextRecord; record
//      })) != null) {
//        import eu.delving.services.core.MetaRepo
//        record.getMob.put(MetaRepo.Record.MODIFIED, modified)
//        record.getMob.put(MetaRepo.Record.DELETED, false)
//        records.insert(record.getMob)
//        ({
//          recordCount += 1; recordCount
//        })
//        if (recordCount % 10000 == 0) {
//          LOG.info(String.format("%d Records read, current count %d", recordCount, records.count))
//        }
//      }
//      LOG.info(String.format("Finally, %d Records read, current count %d", recordCount, records.count))
//      parser.close
    }
    catch {
      case e: Exception => {
        import cake.metaRepo.RecordParseException
        throw new RecordParseException("Unable to parse records", e)
      }
    }
    save(dataSet.copy(state = DataSetState.UPLOADED))
  }

  private def runPullParser(inputStream: InputStream, facts: Facts, dataSet: DataSet, recordSep: String = "record")(codeBlock: String => Unit) {
    import xml.pull._
    import scala.collection.immutable.List
    import scala.collection.mutable.{ListBuffer}
    import xml.{Node, MetaData, Elem, XML}
    import io.Source
    import eu.delving.metadata.MetadataNamespace

    val namespaces = scala.collection.mutable.Map[String, String]()
//    nameSpacePrefixSet.foreach(prefix => namespaces(prefix, e))

    for (ns <- MetadataNamespace.values) {
      namespaces.put(ns.getPrefix, ns.getUri)
    }
    val mdFormat: MetadataFormat = dataSet.details.metadataFormat
    namespaces.put(mdFormat.prefix, mdFormat.namespace)

    val er = new XMLEventReader(Source.fromInputStream(inputStream))

    val sep = if (recordSep.contains(":")) {
      val list: List[String] = recordSep.split(":").toList
      RecordSep(list.head, list.last)
    }
    else {
      RecordSep(null, recordSep)
    }

    var foundRecord = false

    val buffer = new ListBuffer[String]

    val nameSpacePrefixSet = scala.collection.mutable.Set[String]()
    def addToBuffer(event: XMLEvent) {
      buffer += backToXml(event)
    }

    er foreach {
      event: XMLEvent =>
        event match {
          case EvElemStart(sep.pre, sep.label, _, _) =>
            foundRecord = true
            addToBuffer(event)
          case EvElemStart(_, _, attrs: MetaData, _)
            if foundRecord => addToBuffer(event)
          case EvElemEnd(sep.pre, sep.label) =>
            foundRecord = false
            addToBuffer(event)
            codeBlock(buffer.mkString(""))
            buffer.clear()
          case EvElemEnd(_, _) if foundRecord => addToBuffer(event)
          case EvText(text: String) if (foundRecord && text.trim.size > 0) =>
            buffer += text.replaceAll("\\s{2,10}", " ")
          case _ =>
        }
    }

    def backToXml(ev: XMLEvent): String = {
      ev match {
        case EvElemStart(pre, label, attrs, scope) =>
          "<" + writePrefix(pre) + label + attrsToString(attrs) + ">"
        case EvElemEnd(pre, label) =>
          "</" + writePrefix(pre) + label + ">"
        case _ => ""
      }
    }

    def writePrefix(pre: String): String = {
      nameSpacePrefixSet + pre
      pre match {
        case null => ""
        case _ => pre + ":"
      }
    }


    def attrsToString(attrs: MetaData): String = {
      attrs.length match {
        case 0 => ""
        case _ => attrs.map((m: MetaData) => " " + m.key + "='" + m.value + "'").reduceLeft(_ + _)
      }
    }

  }

  def getRecordCount(dataSet: DataSet) : Int = getRecordCount(dataSet.spec)

  def getRecordCount(spec: String): Int = {
    import com.mongodb.casbah.MongoCollection
    val records: MongoCollection = connection("Records." + spec)
    val count: Long = records.count
    count.toInt
  }
}

//object DataSetStateType extends Enumeration {
//
//  case class DataSetState1(state: String) extends Val(state)
//
//  val INCOMPLETE = DataSetState1("incomplete")
//  val DISABLED  = DataSetState1("disabled")
//  val UPLOADED = DataSetState1("uploaded")
//  val QUEUED = DataSetState1("queued")
//  val INDEXING = DataSetState1("indexing")
//  val ENABLED = DataSetState1("enabled")
//  val ERROR = DataSetState1("error")
//}

case class RecordSep(pre: String, label: String, path: Path = new Path())

case class Mapping(recordMapping: RecordMapping,
                   format: MetadataFormat,
                   mapping_hash: String,
                   rec_indexed: Int = 0,
                   errorMessage: Option[String] = Some(""),
                   indexed: Boolean = false) {

  import xml.Elem

  def toXml: Elem = {
    <mapping>
      <name>{format.prefix}</name>
      <rec_indexed>{rec_indexed}</rec_indexed>
      <indexed>{indexed}</indexed>
    </mapping>
  }
}

case class MetadataFormat(prefix: String,
                          schema: String,
                          namespace: String,
                          accessKeyRequired: Boolean = false)

case class Details(
                          name: String,
                          uploaded_records: Int = 0,
                          total_records: Int = 0,
                          deleted_records: Int = 0,
                          metadataFormat: MetadataFormat,
                          facts_bytes: Array[Byte],
                          errorMessage: Option[String] = Some("")
                          )

case class MetadataRecord(
                         metadata: Map[String, String], // this is the raw xml data string
                         modified: Date,
                         deleted: Boolean, // if the record has been deleted
                         uniq: String,
                         hash: Map[String, String]) //extends MetadataRecord
{
  //  import org.bson.types.ObjectId
  //  import com.mongodb.DBObject
  //
  //  def getId: ObjectId
  //
  //  def getUnique: String
  //
  //  def getModifiedDate: Date
  //
  //  def isDeleted: Boolean
  //
  //  def getNamespaces: DBObject
  //
  //  def getHash: DBObject
  //
  //  def getFingerprint: Map[String, Integer]

  def getXmlString(metadataPrefix: String = "raw"): String = {
    metadata.get(metadataPrefix).getOrElse("<dc:title>nothing<dc:title>")
    // todo maybe give back as Elem and check for validity
  }
}

object MetadataRecord extends SalatDAO[MetadataRecord, ObjectId](collection = connection("Records")) {
}

case class PmhRequest(
                             verb: PmhVerb,
                             set: String,
                             from: Option[Date],
                             until: Option[Date],
                             prefix: String
                             ) // extends PmhRequest
{
  def getVerb: PmhVerb = verb

  def getSet: String = set

  def getFrom: Option[Date] = from

  def getUntil: Option[Date] = until

  def getMetadataPrefix: String = prefix
}

case class HarvestStep(       _id: ObjectId = new ObjectId,
                              first: Boolean,
                              exporatopm: Date,
                              listSize: Int,
                              cursor: Int,
                              pmhRequest: PmhRequest,
                              namespaces: Map[String, String],
                              error: String,
                              afterId: ObjectId,
                              nextId: ObjectId
                              )

object HarvestStep extends SalatDAO[HarvestStep, ObjectId](collection = harvestStepsCollection) {

//  def getFirstHarvestStep(verb: PmhVerb, set: String, from: Date, until: Date, metadataPrefix: String, accessKey: String): HarvestStep = {
//
//  }
//
//  def getHarvestStep(resumptionToken: String, accessKey: String): HarvestStep {
//
//  }

//  def removeExpiredHarvestSteps {}
  def removeFirstHarvestSteps(dataSetSpec: String) {
    import com.mongodb.casbah.commons.MongoDBObject
    val step = MongoDBObject("pmhRequest.set," -> dataSetSpec, "first" -> true)
    remove(step)
  }
}
