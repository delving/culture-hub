package models

import java.util.Date
import org.bson.types.ObjectId
import models.salatContext._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.Implicits._
import org.scala_tools.time.Imports._
import controllers.SolrServer
import com.novus.salat._
import dao.SalatDAO
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.MongoCollection
import cake.metaRepo.PmhVerbType.PmhVerb
import eu.delving.sip.{IndexDocument, DataSetState}
import com.mongodb.{BasicDBObject, WriteConcern}
import com.mongodb.casbah.commons.conversions.scala._
import java.io.File
import play.exceptions.ConfigurationException
import eu.delving.metadata.{MetadataNamespace, Path, RecordMapping}
import xml.{NodeSeq, Node, XML}

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since 7/8/11 8:12 AM  
 */

case class DataSet(_id: ObjectId = new ObjectId,
                   spec: String,
                   node: String,
                   user: ObjectId,
                   lockedBy: Option[ObjectId] = None,
                   description: Option[String] = Some(""),
                   state: String, // imported from sip-core
                   details: Details,
                   lastUploaded: DateTime,
                   hashes: Map[String, String] = Map.empty[String, String],
                   namespaces: Map[String, String] = Map.empty[String, String],
                   mappings: Map[String, Mapping] = Map.empty[String, Mapping],
                   hints: Array[Byte] = Array.empty[Byte],
                   invalidRecords: Map[String, List[Int]] = Map.empty[String, List[Int]],
                   access: AccessRight) extends Repository {

  val name = spec

  def getDataSetState: DataSetState = DataSetState.get(state)

  def getUser: User = User.findOneByID(user).get // orElse we are in trouble

  def getLockedBy: Option[User] = if(lockedBy == None) None else User.findOneByID(lockedBy.get)

  def getFacts: Map[String, String] = {
    val initialFacts = (DataSet.factDefinitionList.map(factDef => (factDef.name, ""))).toMap[String, String]
    val storedFacts = (for (fact <- details.facts) yield (fact._1, fact._2.toString)).toMap[String, String]
    initialFacts ++ storedFacts
  }

  def hasHash(hash: String): Boolean = hashes.values.filter(h => h == hash).nonEmpty

  def hasDetails: Boolean = details != null

  def getMetadataFormats(publicCollectionsOnly: Boolean = true): List[RecordDefinition] = {
    val metadataFormats = details.metadataFormat :: mappings.map(mapping => mapping._2.format).toList
    if (publicCollectionsOnly)
      metadataFormats.filter(!_.accessKeyRequired)
    else
      metadataFormats
  }

  def getAllNamespaces: Map[String, String] = {
    val metadataNamespaces = (for (ns <- MetadataNamespace.values) yield (ns.getPrefix, ns.getUri)).toMap[String, String]
    val mdFormatNamespaces = Map(details.metadataFormat.prefix -> details.metadataFormat.namespace)
    metadataNamespaces ++ mdFormatNamespaces
  }

  def setMapping(mapping: RecordMapping, accessKeyRequired: Boolean = true): DataSet = {
    import eu.delving.metadata.MetadataNamespace

    val ns: Option[MetadataNamespace] = MetadataNamespace.values().filter(ns => ns.getPrefix == mapping.getPrefix).headOption
    if (ns == None) {
      throw new MetaRepoSystemException(String.format("Namespace prefix %s not recognized", mapping.getPrefix))
    }
    val newMapping = Mapping(recordMapping = RecordMapping.toXml(mapping), format = RecordDefinition(ns.get.getPrefix, ns.get.getSchema, ns.get.getUri, accessKeyRequired))
    // remove First Harvest Step
    this.copy(mappings = this.mappings.updated(mapping.getPrefix, newMapping))
  }
}

object DataSet extends SalatDAO[DataSet, ObjectId](collection = dataSetsCollection) with SolrServer with AccessControl {

  RegisterJodaTimeConversionHelpers()

  lazy val factDefinitionList = parseFactDefinitionList

  private def parseFactDefinitionList: Seq[FactDefinition] = {
    val file = new File("conf/fact-definition-list.xml")
    if (!file.exists()) throw new ConfigurationException("Fact definition configuration file not found!")
    val xml = XML.loadFile(file)
    for (e <- (xml \ "fact-definition")) yield parseFactDefinition(e)
  }

  private def parseFactDefinition(node: Node) = {
    FactDefinition(
      node \ "@name" text,
      node \ "prompt" text,
      node \ "toolTip" text,
      (node \ "automatic" text).equalsIgnoreCase("true"),
      for (option <- (node \ "options" \ "string")) yield (option text)
    )
  }

  def findCollectionForIndexing() : Option[DataSet] = {
    import eu.delving.sip.DataSetState._
    val allDateSets: List[DataSet] = find(MongoDBObject("state" -> INDEXING.toString)).sort(MongoDBObject("name" -> 1)).toList
    if (allDateSets.length < 3)
      {
        val queuedIndexing = find(MongoDBObject("state" -> QUEUED.toString)).sort(MongoDBObject("name" -> 1)).toList
        queuedIndexing.headOption
      }
    else
      None
  }

  import eu.delving.sip.IndexDocument
  import org.apache.solr.common.SolrInputDocument

  // FIXME: this assumes that the spec is unique accross all users
  def findBySpec(spec: String): Option[DataSet] = findOne(MongoDBObject("spec" -> spec))

  def retrieveBySpec(spec: String): DataSet = findBySpec(spec).getOrElse(throw new DataSetNotFoundException(String.format("String %s does not exist", spec)))

  def findAll(publicCollectionsOnly: Boolean = true) = {
    val allDateSets: List[DataSet] = find(MongoDBObject()).sort(MongoDBObject("name" -> 1)).toList
    if (publicCollectionsOnly)
      allDateSets.filter(ds => !ds.details.metadataFormat.accessKeyRequired || ds.mappings.forall(ds => ds._2.format.accessKeyRequired == false))
    else
      allDateSets
  }

  def findAllForUser(user: User) = {
    val dataSetCursor = findAllByRight(user.reference.username, user.reference.node, "read")
    (for(ds <- dataSetCursor) yield grater[DataSet].asObject(ds)).toList
  }

  def findAllByOwner(owner: ObjectId) = DataSet.find(MongoDBObject("user" -> owner)).toList


  def updateById(id: ObjectId, dataSet: DataSet) {
    update(MongoDBObject("_id" -> dataSet._id), dataSet, false, false, new WriteConcern())
  }

  def upsertById(id: ObjectId, dataSet: DataSet) {
    update(MongoDBObject("_id" -> dataSet._id), dataSet, true, false, new WriteConcern())
  }

  def updateBySpec(spec: String, dataSet: DataSet) {
    update(MongoDBObject("spec" -> dataSet.spec), dataSet, false, false, new WriteConcern())
  }

  def updateState(dataSet: DataSet, state: DataSetState) {
    update(MongoDBObject("_id" -> dataSet._id), MongoDBObject("$set" -> MongoDBObject("state" -> state.toString)), false, false, new WriteConcern())
  }

  def updateGroups(dataSet: DataSet, groups: List[String]) {
    update(MongoDBObject("_id" -> dataSet._id), MongoDBObject("$set" -> MongoDBObject("access.groups" -> groups)), false, false, new WriteConcern())
  }

  def addHash(dataSet: DataSet, key: String, hash: String) {
    update(MongoDBObject("_id" -> dataSet._id), MongoDBObject("$set" -> MongoDBObject(("hashes." + key) -> hash)))
  }

  def delete(dataSet: DataSet) {
    connection("Records." + dataSet.spec).drop()
    remove(dataSet)
  }

  // TODO should we cache the constructions of these objects?
  def getRecords(dataSet: DataSet): SalatDAO[MetadataRecord, ObjectId] with MDR = {
    val recordCollection: MongoCollection = connection("Records." + dataSet.spec)
    recordCollection.ensureIndex(MongoDBObject("localRecordKey" -> 1, "globalHash" -> 1))
    object CollectionMDR extends SalatDAO[MetadataRecord, ObjectId](recordCollection) with MDR
    CollectionMDR
  }

  def getRecord(identifier: String, metadataFormat: String, accessKey: String): Option[MetadataRecord] = {
    import org.bson.types.ObjectId
    val parsedId = identifier.split(":")
    // throw exception for illegal id construction
    val spec = parsedId.head
    val recordId = parsedId.last
    val ds: Option[DataSet] = findBySpec(spec)
    val record: Option[MetadataRecord] = getRecords(ds.get).findOneByID(new ObjectId(recordId))
    // throw RecordNotFoundException
    if (record == None) throw new RecordNotFoundException("Unable to find record for " + identifier)
    if (record.get.rawMetadata.contains(metadataFormat))
      record
    else {
      val mappedRecord = record.get
      val solrDoc: IndexDocument = transFormXml(metadataFormat, ds.get, mappedRecord)
      Some(mappedRecord.copy(mappedMetadata = Map[String, IndexDocument](metadataFormat -> solrDoc)))
    }
  }

  def deleteFromSolr(dataSet: DataSet) {
    import org.apache.solr.client.solrj.response.UpdateResponse
    val deleteResponse: UpdateResponse = getStreamingUpdateServer.deleteByQuery("delving_spec:" + dataSet.spec)
    deleteResponse.getStatus
    getStreamingUpdateServer.commit
  }

  def createSolrInputDocument(indexDoc: IndexDocument): SolrInputDocument = {
    import scala.collection.JavaConversions._

    val doc = new SolrInputDocument
    indexDoc.getMap.entrySet().foreach {
      entry =>
        val unMungedKey = entry.getKey // todo later unmunge the key with namespaces.replaceAll("_", ":")
        entry.getValue.foreach(
          value =>
            doc.addField(unMungedKey, value.toString)
        )
    }
    doc
  }

  def addDelvingHouseKeepingFields(inputDoc: SolrInputDocument, dataSet: DataSet, record: MetadataRecord, format: String) {
    import scala.collection.JavaConversions._

    inputDoc.addField("delving_pmhId", "%s_%s".format(dataSet.spec, record._id.toString))
    inputDoc.addField("delving_spec", "%s".format(dataSet.spec))
    inputDoc.addField("delving_currentFormat", format)
    dataSet.getMetadataFormats(true).foreach(format => inputDoc.addField("delving_publicFormats", format.prefix))
    dataSet.getMetadataFormats(false).foreach(format => inputDoc.addField("delving_allFormats", format.prefix))


    val europeanaUri = "europeana_uri"
    if (inputDoc.containsKey(europeanaUri))
      inputDoc.addField("id", inputDoc.getField(europeanaUri).getValues.headOption.getOrElse("empty"))
    else if (!record.localRecordKey.isEmpty)
      inputDoc.addField("id", "%s_%s".format(dataSet.spec, record.localRecordKey))
    else
      inputDoc.addField("id", "%s_%s".format(dataSet.spec, record._id.toString))
    // todo add more elements: hasDigitalObject. etc
  }

  def getStateWithSpec(spec: String): String = findBySpec(spec).get.state

  def indexInSolr(dataSet: DataSet, metadataFormatForIndexing: String) : (Int, Int) = {
    import eu.delving.sip.MappingEngine
    import scala.collection.JavaConversions.asJavaMap
    println(dataSet.spec)
    val salatDAO = getRecords(dataSet)
    DataSet.updateState(dataSet, DataSetState.INDEXING)
    val mapping = dataSet.mappings.get(metadataFormatForIndexing)
    if (mapping == None) throw new MappingNotFoundException("Unable to find mapping for " + metadataFormatForIndexing)
    val engine: MappingEngine = new MappingEngine(mapping.get.recordMapping, asJavaMap(dataSet.namespaces), play.Play.classloader.getParent)
    val cursor = salatDAO.find(MongoDBObject())
    var state = getStateWithSpec(dataSet.spec)
    for (record <- cursor; if (state.equals(DataSetState.INDEXING.toString))) {
      println(cursor.numSeen)
      println(record._id)
      println(record.localRecordKey)
      if (cursor.numSeen % 100 == 0) {
        state = getStateWithSpec(dataSet.spec)
      }
      // very fast
//      val doc = new SolrInputDocument
//      doc.addField("id", record._id)
//      doc.addField("text_value", record.getXmlString())
//      for (i <- 0 to 100) doc.addField("dummy", i.toString)
//      getStreamingUpdateServer.add(doc)
      // very slow
      val s0 = System.currentTimeMillis()
      val mapping = engine.executeMapping(record.getXmlString())
//      println("mapping in: %s".format(System.currentTimeMillis() - s0))
      val s1 = System.currentTimeMillis()
      mapping match {
        case indexDoc: IndexDocument => {
          val doc = createSolrInputDocument(indexDoc)
          addDelvingHouseKeepingFields(doc, dataSet, record, metadataFormatForIndexing)
          getStreamingUpdateServer.add(doc)
        }
        case _ => // catching null
      }
//      println("processing in: %s".format(System.currentTimeMillis() - s1))
    }

//    val counter: (Int, Int) = cursor.foldLeft((0, 0)) {
//      (recordsProcessed, record) => {
//        if (recordsProcessed._1 % 50 == 0)
//          println("Indexeded %d records, and discarded %d".format(recordsProcessed._1, recordsProcessed._2))
//        //          if (continueIndexing(dataSet.spec)) return (0,0) // todo find a more elegant method for breaking
//        val mappingDoc = engine.executeMapping(record.getXmlString())
//        //        mappingDoc match {
//        //          case IndexDocument => {
//        //            val doc: SolrInputDocument = createSolrInputDocument(mappingDoc)
//        //            addDelvingHouseKeepingFields(doc, dataSet, record, metadataFormatForIndexing)
//        //            getStreamingUpdateServer.add(doc)
//        //            (recordsProcessed._1 + 1, recordsProcessed._2)
//        //          }
//        //          case _ => // catching null
//        //            (recordsProcessed._1, recordsProcessed._2 + 1)
//        //        }
//        (recordsProcessed._1 + 1, recordsProcessed._2)
//      }
//    }
    state match {
      case "INDEXING" =>
        DataSet.updateState(dataSet, DataSetState.ENABLED)
        getStreamingUpdateServer.commit()
      case _ =>
//        getStreamingUpdateServer.rollback() // todo find out what this does
        println("deleting dataset from solr " + dataSet.spec)
        DataSet.deleteFromSolr(dataSet)
    }
    println(engine.toString)
    (1, 0)
  }

  def getRecordCount(dataSet: DataSet): Int = getRecordCount(dataSet.spec)

  def getRecordCount(spec: String): Int = {
    import com.mongodb.casbah.MongoCollection
    val records: MongoCollection = connection("Records." + spec)
    val count: Long = records.count
    count.toInt
  }

  def getMetadataFormats(publicCollectionsOnly: Boolean = true): List[RecordDefinition] = {
    val metadataFormats = findAll(publicCollectionsOnly).flatMap{
      ds =>
        ds.getMetadataFormats(publicCollectionsOnly)
    }
    metadataFormats.toList.distinct
  }

  def getMetadataFormats(spec: String, accessKey: String): List[RecordDefinition] = {
    // todo add accessKey checker
    val accessKeyIsValid: Boolean = true
    findBySpec(spec) match {
      case ds: Some[DataSet] => ds.get.getMetadataFormats(accessKeyIsValid)
      case None => List[RecordDefinition]()
    }
  }

  def transFormXml(prefix: String, dataSet: DataSet, record: MetadataRecord): IndexDocument = {
    import eu.delving.sip.MappingEngine
    import scala.collection.JavaConversions.asJavaMap
    val mapping = dataSet.mappings.get(prefix)
    if (mapping == None) throw new MappingNotFoundException("Unable to find mapping for " + prefix)
    val engine: MappingEngine = new MappingEngine(mapping.get.recordMapping, asJavaMap(dataSet.namespaces), play.Play.classloader)
    val mappedRecord: IndexDocument = engine.executeMapping(record.getXmlString())
    mappedRecord
  }
  // access control

  protected def getCollection = dataSetsCollection

  protected def getObjectIdField = "spec"
}

case class FactDefinition(name: String, prompt: String, tooltip: String, automatic: Boolean = false, options: Seq[String]) {
  def hasOptions = !options.isEmpty
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

case class Mapping(recordMapping: String = "",
                   format: RecordDefinition,
                   rec_indexed: Int = 0,
                   errorMessage: Option[String] = Some(""),
                   indexed: Boolean = false)

case class RecordDefinition(prefix: String,
                          schema: String,
                          namespace: String,
                          accessKeyRequired: Boolean = false)

object RecordDefinition {

  val RECORD_DEFINITION_SUFFIX = "-record-definition.xml"

  lazy val recordDefinitions = parseRecordDefinitions

  private def parseRecordDefinitions: List[RecordDefinition] = {
    val conf = new File("conf/")
    val definitionContent = for(f <- conf.listFiles().filter(f => f.isFile && f.getName.endsWith(RECORD_DEFINITION_SUFFIX))) yield XML.loadFile(f)
    definitionContent flatMap { parseRecordDefinition(_) } toList
  }

  private def parseRecordDefinition(node: Node): Option[RecordDefinition] = {
    val prefix = node \ "@prefix" text
    val recordDefinitionNamespace: Node = node \ "namespaces" \ "namespace" find {_.attributes("prefix").exists(_.text == prefix) } getOrElse (return None)
    Some(RecordDefinition(recordDefinitionNamespace \ "@prefix" text, recordDefinitionNamespace \ "@schema" text, recordDefinitionNamespace \ "@uri" text))
  }

  def create(prefix: String, accessKeyRequired: Boolean = true): RecordDefinition = {
    import eu.delving.metadata.MetadataNamespace

    val ns: MetadataNamespace = MetadataNamespace.values().filter(ns => ns.getPrefix == prefix).headOption.getOrElse(
      throw new MetaRepoSystemException(String.format("Namespace prefix %s not recognized", prefix))
    )
    RecordDefinition(ns.getPrefix, ns.getSchema, ns.getUri, accessKeyRequired)
  }
}

case class Details(name: String,
                   uploaded_records: Int = 0,
                   total_records: Int = 0,
                   deleted_records: Int = 0,
                   metadataFormat: RecordDefinition,
                   facts: BasicDBObject = new BasicDBObject(),
                   errorMessage: Option[String] = Some("")
                  ) {

  def getFactsAsText: String = {
    import com.mongodb.casbah.Implicits._
    val builder = new StringBuilder
    facts foreach {
      fact => builder.append(fact._1).append("=").append(fact._2).append("\n")
    }
    builder.toString()
  }


}

case class MetadataRecord(_id: ObjectId = new ObjectId,
                          rawMetadata: Map[String, String], // this is the raw xml data string
                          mappedMetadata: Map[String, IndexDocument] = Map.empty[String, IndexDocument], // this is the mapped xml data string only added after transformation
                          modified: DateTime = DateTime.now,
                          validOutputFormats: List[String] = List.empty[String],
                          deleted: Boolean = false, // if the record has been deleted
                          localRecordKey: String, // the unique element value
                          globalHash: String, // the hash of the raw content
                          hash: Map[String, String] // the hash for each field, for duplicate detection
                         ) {

  def getXmlString(metadataPrefix: String = "raw"): String = {
    if (rawMetadata.contains(metadataPrefix)) {
      rawMetadata.get(metadataPrefix).get
    }
    else if (mappedMetadata.contains(metadataPrefix)) {
      import scala.collection.JavaConversions._
      val indexDocument = mappedMetadata.get(metadataPrefix).get
      indexDocument.getMap.entrySet().foldLeft("")(
        (output, indexDoc) => {
          val unMungedKey = indexDoc.getKey // todo later unmunge the key with namespaces.replaceAll("_", ":")
          output + indexDoc.getValue.map(value => {
            "<%s>%s</%s>".format(unMungedKey, value.toString, unMungedKey)
          }).mkString
        }
      )
    }
    else
      throw new RecordNotFoundException("Unable to find record with source metadata prefix: %s".format(metadataPrefix))
  }

  def getXmlStringAsRecord(metadataPrefix: String = "raw"): String = {
    "<record>%s</record>".format(getXmlString(metadataPrefix))
  }

}

trait MDR {
  self: SalatDAO[MetadataRecord, ObjectId] =>

  def existsByLocalRecordKey(key: String) = {
    count(MongoDBObject("localRecordKey" -> key)) > 0
  }

  def findByLocalRecordKey(key: String) = {
    findOne(MongoDBObject("localRecordKey" -> key))
  }

  def upsertByLocalKey(updatedRecord: MetadataRecord) {
    update(MongoDBObject("localRecordKey" -> updatedRecord.localRecordKey), updatedRecord, true, false, new WriteConcern())
  }
}


case class PmhRequest(
                             verb: PmhVerb,
                             set: String,
                             from: Option[Date],
                             until: Option[Date],
                             prefix: String
                             ) {


  // extends PmhRequest {
  def getVerb: PmhVerb = verb

  def getSet: String = set

  def getFrom: Option[Date] = from

  def getUntil: Option[Date] = until

  def getMetadataPrefix: String = prefix
}

case class HarvestStep(_id: ObjectId = new ObjectId,
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


class AccessKeyException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}

class UnauthorizedException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}

class BadArgumentException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}

class DataSetNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}

class HarvindexingException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}

class MappingNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}

class RecordNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}

class MetaRepoSystemException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}

class RecordParseException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}

class ResumptionTokenNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}