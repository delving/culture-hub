package models

import java.util.Date
import org.bson.types.ObjectId
import models.salatContext._
import com.mongodb.casbah.Imports._
import controllers.SolrServer
import com.novus.salat._
import dao.SalatDAO
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.MongoCollection
import cake.metaRepo.PmhVerbType.PmhVerb
import com.mongodb.{BasicDBObject, WriteConcern}
import java.io.File
import play.exceptions.ConfigurationException
import eu.delving.metadata.{Path, RecordMapping}
import xml.{Node, XML}
import cake.ComponentRegistry
import play.i18n.Messages
import eu.delving.sip.IndexDocument

/**
 * DataSet model
 * The unique ID for this model is the mongo _id. IF YOU WANT TO USE THE SPEC, ALWAYS ALSO USE THE ORG_ID. The spec alone does not provide for unicity accross organizations!
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since 7/8/11 8:12 AM  
 */

case class DataSet(_id: ObjectId = new ObjectId,
                   spec: String,
                   user_id: ObjectId, // who created this
                   orgId: Predef.String,
                   lockedBy: Option[ObjectId] = None,
                   description: Option[String] = Some(""),
                   state: DataSetState,
                   visibility: Visibility,
                   deleted: Boolean = false,
                   details: Details,
                   lastUploaded: Date,
                   hashes: Map[String, String] = Map.empty[String, String],
                   namespaces: Map[String, String] = Map.empty[String, String],
                   mappings: Map[String, Mapping] = Map.empty[String, Mapping],
                   idxMappings: List[String] = List.empty[String],
                   hints: Array[Byte] = Array.empty[Byte],
                   invalidRecords: Map[String, List[Int]] = Map.empty[String, List[Int]]) {

  val name = spec

  def getCreator: User = User.findOneByID(user_id).get // orElse we are in trouble

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
    val metadataNamespaces = RecordDefinition.recordDefinitions.map(rd => (rd.prefix, rd.namespace)).toMap[String, String]
    val mdFormatNamespaces = Map(details.metadataFormat.prefix -> details.metadataFormat.namespace)
    metadataNamespaces ++ mdFormatNamespaces
  }

  def setMapping(mapping: RecordMapping, accessKeyRequired: Boolean = true): DataSet = {
    val ns: Option[RecordDefinition] = RecordDefinition.recordDefinitions.filter(rd => rd.prefix == mapping.getPrefix).headOption
    if (ns == None) {
      throw new MetaRepoSystemException(String.format("Namespace prefix %s not recognized", mapping.getPrefix))
    }
    val newMapping = Mapping(recordMapping = Some(RecordMapping.toXml(mapping)), format = RecordDefinition(ns.get.prefix, ns.get.schema, ns.get.namespace, accessKeyRequired))
    // remove First Harvest Step
    this.copy(mappings = this.mappings.updated(mapping.getPrefix, newMapping))
  }
}

object DataSet extends SalatDAO[DataSet, ObjectId](collection = dataSetsCollection) with Pager[DataSet] with SolrServer {

  lazy val factDefinitionList = parseFactDefinitionList

  def getFactDefinitionFile: File = {
    val file = new File("conf/fact-definition-list.xml")
    if (!file.exists()) throw new ConfigurationException("Fact definition configuration file not found!")
    file
  }

  private def parseFactDefinitionList: Seq[FactDefinition] = {
    val xml = XML.loadFile(getFactDefinitionFile)
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

  def findByState(state: DataSetState) = {
    DataSet.find(MongoDBObject("state.name" -> state.name, "deleted" -> false))
  }

  def getIndexingState(orgId: String, spec: String): (Int, Int) = {
    val ds = DataSet.findBySpecAndOrgId(orgId, spec).getOrElse(return (100, 100))
    if(ds.state == DataSetState.ENABLED) return (ds.details.total_records, ds.details.total_records)
    (ds.details.indexing_count, ds.details.total_records)
  }

  def findCollectionForIndexing() : Option[DataSet] = {
    val allDateSets: List[DataSet] = findByState(DataSetState.INDEXING).sort(MongoDBObject("name" -> 1)).toList
    if (allDateSets.length < 3) {
        val queuedIndexing = findByState(DataSetState.QUEUED).sort(MongoDBObject("name" -> 1)).toList
        queuedIndexing.headOption
      } else {
        None
    }
  }


  // FIXME: this assumes that the spec is unique accross all users
  @Deprecated
  def findBySpec(spec: String): Option[DataSet] = findOne(MongoDBObject("spec" -> spec, "deleted" -> false))

  def findBySpecAndOrgId(spec: String, orgId: String): Option[DataSet] = findOne(MongoDBObject("spec" -> spec, "orgId" -> orgId, "deleted" -> false))

  def findAll(publicCollectionsOnly: Boolean = true) = {
    val allDateSets: List[DataSet] = find(MongoDBObject("deleted" -> false)).sort(MongoDBObject("name" -> 1)).toList
    if (publicCollectionsOnly)
      allDateSets.filter(ds => !ds.details.metadataFormat.accessKeyRequired || ds.mappings.forall(ds => ds._2.format.accessKeyRequired == false))
    else
      allDateSets
  }

  def findAllForUser(userName: String): List[DataSet] =
    Group.
            find(MongoDBObject("users" -> userName)).
            filter(g => g.grantType == GrantType.MODIFY || g.grantType == GrantType.OWN).
            map(g => if(g.grantType == GrantType.OWN) DataSet.findAllByOrgId(g.orgId).toList else DataSet.find("_id" $in g.dataSets).toList).
            toList.flatten.distinct

  def findAllByOrgId(orgId: String) = DataSet.find(MongoDBObject("orgId" -> orgId, "deleted" -> false))

  def updateById(id: ObjectId, dataSet: DataSet) {
    update(MongoDBObject("_id" -> dataSet._id), dataSet, false, false, new WriteConcern())
  }

  def upsertById(id: ObjectId, dataSet: DataSet) {
    update(MongoDBObject("_id" -> dataSet._id), dataSet, true, false, new WriteConcern())
  }

  def updateState(dataSet: DataSet, state: DataSetState) {
    val sdbo: MongoDBObject = grater[DataSetState].asDBObject(state)
    update(MongoDBObject("_id" -> dataSet._id), MongoDBObject("$set" -> MongoDBObject("state" -> sdbo)), false, false, new WriteConcern())
  }

  def addHash(dataSet: DataSet, key: String, hash: String) {
    update(MongoDBObject("_id" -> dataSet._id), MongoDBObject("$set" -> MongoDBObject(("hashes." + key) -> hash)))
  }

  def delete(dataSet: DataSet) {
    // TODO rename these for the moment
    connection(getRecordsCollectionName(dataSet)).rename(getRecordsCollectionName(dataSet) + "_" + dataSet._id.toString)
    update(MongoDBObject("_id" -> dataSet._id), $set ("deleted" -> true), false, false)
  }

  def getRecordsCollectionName(dataSet: DataSet) = "Records.%s_%s".format(dataSet.orgId, dataSet.spec)

  // TODO should we cache the constructions of these objects?
  def getRecords(dataSet: DataSet): SalatDAO[MetadataRecord, ObjectId] with MDR  = {
    val recordCollection: MongoCollection = connection(getRecordsCollectionName(dataSet))
    recordCollection.ensureIndex(MongoDBObject("localRecordKey" -> 1, "globalHash" -> 1))
    object CollectionMDR extends SalatDAO[MetadataRecord, ObjectId](recordCollection) with MDR
    CollectionMDR
  }

  def getRecord(identifier: String, metadataFormat: String, accessKey: String): Option[MetadataRecord] = {
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

  @Deprecated
  def getStateBySpec(spec: String) = DataSet.findBySpec(spec).get.state

  def getStateBySpecAndOrgId(spec: String, orgId: String) = DataSet.findBySpecAndOrgId(spec, orgId).get.state

  def changeState(dataSet: DataSet, state: DataSetState): DataSet = {
    val dataSetLatest = DataSet.findBySpec(dataSet.spec).get
    val mappings = dataSetLatest.mappings.transform((key, map) => map.copy(rec_indexed = 0))
    val updatedDataSet = dataSetLatest.copy(state = state, mappings = mappings)
    DataSet.save(updatedDataSet)
    updatedDataSet
  }

  def addIndexingMapping(dataSet: DataSet, mapping: String) {
    DataSet.update(MongoDBObject("_id" -> dataSet._id), $addToSet("idxMappings" -> mapping))
  }

  def updateIndexingCount(dataSet: DataSet, count: Int) {
    DataSet.update(MongoDBObject("_id" -> dataSet._id), MongoDBObject("$set" -> MongoDBObject("details.indexing_count" -> count)))
  }

  def getRecordCount(dataSet: DataSet): Int = {
    val records: MongoCollection = connection(getRecordsCollectionName(dataSet))
    val count: Long = records.count
    count.toInt
  }

  def getMetadataFormats(publicCollectionsOnly: Boolean = true): List[RecordDefinition] = {
    val metadataFormats = findAll(publicCollectionsOnly).flatMap {
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
    val engine: MappingEngine = new MappingEngine(mapping.get.recordMapping.getOrElse(""), asJavaMap(dataSet.namespaces), play.Play.classloader, ComponentRegistry.metadataModel)
    val mappedRecord: IndexDocument = engine.executeMapping(record.getXmlString())
    mappedRecord
  }
}

case class FactDefinition(name: String, prompt: String, tooltip: String, automatic: Boolean = false, options: Seq[String]) {
  def hasOptions = !options.isEmpty

  val opts = options.map(opt => (opt, opt))
}

case class DataSetState(name: String) {
  def description = Messages.get("dataSetState." + name.toLowerCase)
}

object DataSetState {
  val values = List(INCOMPLETE, UPLOADED, QUEUED, INDEXING, DISABLED, ERROR)
  val INCOMPLETE = DataSetState("incomplete")
  val UPLOADED = DataSetState("uploaded")
  val QUEUED = DataSetState("queued")
  val INDEXING = DataSetState("indexing")
  val ENABLED = DataSetState("enabled")
  val DISABLED = DataSetState("disabled")
  val ERROR = DataSetState("error")
  def withName(name: String): Option[DataSetState] = if(valid(name)) Some(DataSetState(name)) else None
  def valid(name: String) = values.contains(DataSetState(name))
}

case class RecordSep(pre: String, label: String, path: Path = new Path())

case class Mapping(recordMapping: Option[String] = None,
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

  def recordDefinitions = parseRecordDefinitions

  def getRecordDefinitionFiles: Seq[File] = {
    val conf = new File("conf/")
    conf.listFiles().filter(f => f.isFile && f.getName.endsWith(RECORD_DEFINITION_SUFFIX))
  }

  private def parseRecordDefinitions: List[RecordDefinition] = {
    val definitionContent = getRecordDefinitionFiles.map { f => XML.loadFile(f) }
    definitionContent.flatMap(parseRecordDefinition(_)).toList
  }

  private def parseRecordDefinition(node: Node): Option[RecordDefinition] = {
    val prefix = node \ "@prefix" text
    val recordDefinitionNamespace: Node = node \ "namespaces" \ "namespace" find {_.attributes("prefix").exists(_.text == prefix) } getOrElse (return None)
    Some(RecordDefinition(recordDefinitionNamespace \ "@prefix" text, recordDefinitionNamespace \ "@schema" text, recordDefinitionNamespace \ "@uri" text))
  }

}

case class Details(name: String,
                   uploaded_records: Int = 0,
                   total_records: Int = 0,
                   deleted_records: Int = 0,
                   indexing_count: Int = 0,
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
                          modified: Date = new Date(),
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

class SolrConnectionException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}