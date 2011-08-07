package models

import java.util.Date
import org.bson.types.ObjectId
import models.salatContext._
import com.mongodb.casbah.Imports._
import controllers.SolrServer
import eu.delving.metadata.{Path, RecordMapping}
import eu.delving.sip.DataSetState
import com.mongodb.WriteConcern
import com.novus.salat._
import dao.SalatDAO
import com.mongodb.casbah.MongoCollection
import cake.metaRepo.PmhVerbType.PmhVerb

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since 7/8/11 8:12 AM  
 */

case class DataSet(_id: ObjectId = new ObjectId,
                   spec: String,
                   state: String, // imported from sip-core
                   details: Details,
                   facts_hash: String,
                   source_hash: String = "",
                   downloaded_source_hash: Option[String] = Some(""),
                   namespaces: Map[String, String] = Map.empty[String, String],
                   mappings: Map[String, Mapping] = Map.empty[String, Mapping],
                   access: AccessRight) {

  import xml.Elem

  def getDataSetState: DataSetState = DataSetState.get(state)

  def getHashes: List[String] = {
    val mappingList = mappings.values.map(_.mapping_hash).toList
    val hashes: List[String] = facts_hash :: source_hash :: mappingList
    hashes.filterNot(_.isEmpty)
  }

  def hasHash(hash: String): Boolean = getHashes.contains(hash)

  // todo update sip-creator with richer info.
  def toXml: Elem = {
    <dataset>
      <spec>{spec}</spec>
      <name>{details.name}</name>
      <state>{state.toString}</state>
      <recordCount>{details.total_records}</recordCount>
      <!--uploadedRecordCount>{details.uploaded_records}</uploadedRecordCount-->
      <recordsIndexed deprecated="This item will be removed later. See mappings">0</recordsIndexed>
      <hashes>{getHashes.map(hash => <string>{hash}</string>)}</hashes>
      <!--errorMessage>{details.errorMessage}</errorMessage>
      <mappings>{mappings.values.map{mapping => mapping.toXml}}</mappings-->
    </dataset>
  }

  def hasDetails: Boolean = details != null

  def getMetadataFormats(publicCollectionsOnly: Boolean = true): List[MetadataFormat] = {
    val metadataFormats = details.metadataFormat :: mappings.map(mapping => mapping._2.format).toList
    if (publicCollectionsOnly)
      metadataFormats.filter(!_.accessKeyRequired)
    else
      metadataFormats
  }

  def setMapping(mapping: RecordMapping, hash: String, accessKeyRequired: Boolean = true): DataSet = {
    import eu.delving.metadata.MetadataNamespace

    val ns: Option[MetadataNamespace] = MetadataNamespace.values().filter(ns => ns.getPrefix == mapping.getPrefix).headOption
    if (ns == None) {
      throw new MetaRepoSystemException(String.format("Namespace prefix %s not recognized", mapping.getPrefix))
    };
    val newMapping = Mapping(recordMapping = RecordMapping.toXml(mapping),
      format = MetadataFormat(ns.get.getPrefix, ns.get.getSchema, ns.get.getUri, accessKeyRequired),
      mapping_hash = hash)
    // remove First Harvest Step
    this.copy(mappings = this.mappings.updated(mapping.getPrefix, newMapping))
  }

}

object DataSet extends SalatDAO[DataSet, ObjectId](collection = dataSetsCollection) with SolrServer with AccessControl {

  import com.mongodb.casbah.commons.MongoDBObject

  def getWithSpec(spec: String): DataSet = find(spec).getOrElse(throw new DataSetNotFoundException(String.format("String %s does not exist", spec)))

  def findAll(publicCollectionsOnly: Boolean = true) = {
    val allDateSets: List[DataSet] = find(MongoDBObject()).sort(MongoDBObject("name" -> 1)).toList
    if (publicCollectionsOnly)
      allDateSets.filter(ds => !ds.details.metadataFormat.accessKeyRequired || ds.mappings.forall(ds => ds._2.format.accessKeyRequired == false))
    else
      allDateSets
  }

  def findAllForUser(user: User) = {
    val dataSetCursor = DataSet.findAllByRight(user.reference.username, user.reference.node, "read")
    (for(ds <- dataSetCursor) yield grater[DataSet].asObject(ds)).toList
  }

  def updateById(id: ObjectId, dataSet: DataSet) {
    update(MongoDBObject("_id" -> dataSet._id), dataSet, false, false, new WriteConcern())
  }

  def upsertById(id: ObjectId, dataSet: DataSet) {
    update(MongoDBObject("_id" -> dataSet._id), dataSet, true, false, new WriteConcern())
  }

  def delete(dataSet: DataSet) {
    connection("Records." + dataSet.spec).drop()
    remove(dataSet)
  }

  def getRecords(dataSet: DataSet): SalatDAO[MetadataRecord, ObjectId] with MDR = {
    val recordCollection: MongoCollection = connection("Records." + dataSet.spec)
    recordCollection.ensureIndex(MongoDBObject("localRecordKey" -> 1, "globalHash" -> 1))
    object CollectionMDR extends SalatDAO[MetadataRecord, ObjectId](recordCollection) with MDR
    CollectionMDR
  }

  def parseIdentifier(identifier: String): RecordIdentifier = {
    val parsedId = identifier.split(":")
    // throw exception for illegal id construction
    if (parsedId.length != 2) throw new RecordNotFoundException("Unable to parse record identifier %s".format(identifier))
    val spec = parsedId.head
    val recordId = parsedId.last
    val ds: Option[DataSet] = find(spec)
    // if none throw exception
    if (ds == None) throw new DataSetNotFoundException("Unable to find collection with spec %s".format(spec))
    RecordIdentifier(recordId, spec, ds.get, identifier)
  }

  def getMappedRecord(mdRecord: MetadataRecord, recId: RecordIdentifier, metadataFormat: String, accessKey: String, sourceMetadataFormat: String = "raw"): String = {
    if (mdRecord.metadata.keySet.contains(metadataFormat))
      "<record>%s</record>".format(mdRecord.getXmlString(metadataFormat))
    else
      transFormXml(metadataFormat, recId.dataSet, mdRecord)
  }

  def getMappedRecord(identifier: String, metadataFormat: String, accessKey: String, sourceMetadataFormat: String): String = {
    val recordId: RecordIdentifier = parseIdentifier(identifier)
    val sourceRecord: MetadataRecord = getRecord(recordId, metadataFormat, accessKey, sourceMetadataFormat)
    getMappedRecord(sourceRecord, recordId, metadataFormat, accessKey, sourceMetadataFormat)
  }

  def getRecord(recordId: RecordIdentifier, metadataFormat: String, accessKey: String, sourceMetadataFormat: String = "raw"): MetadataRecord = {
    import org.bson.types.ObjectId
    val record: Option[MetadataRecord] = getRecords(recordId.dataSet).findOneByID(new ObjectId(recordId.identifier))
    if(record == None) throw new RecordNotFoundException("Could not find record %s in dataset %s".format(recordId.identifier, recordId.spec))
    record.get
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

  def getRecordCount(dataSet: DataSet): Int = getRecordCount(dataSet.spec)

  def getRecordCount(spec: String): Int = {
    import com.mongodb.casbah.MongoCollection
    val records: MongoCollection = connection("Records." + spec)
    val count: Long = records.count
    count.toInt
  }

  def getMetadataFormats(publicCollectionsOnly: Boolean = true): List[MetadataFormat] = {
    val metadataFormats = findAll(publicCollectionsOnly).flatMap{
      ds =>
        ds.getMetadataFormats(publicCollectionsOnly)
    }
    metadataFormats.toList.distinct
  }

  def getMetadataFormats(spec: String, accessKey: String): List[MetadataFormat] = {
    // todo add accessKey checker
    val accessKeyIsValid: Boolean = true
    find(spec) match {
      case ds: Some[DataSet] => ds.get.getMetadataFormats(accessKeyIsValid)
      case None => List[MetadataFormat]()
    }
  }

  def transFormXml(prefix: String, dataSet: DataSet, record: MetadataRecord): String = {
    import eu.delving.sip.MappingEngine
    import scala.collection.JavaConversions.asJavaMap
    val mapping = dataSet.mappings.get(prefix)
    if (mapping == None) throw new MappingNotFoundException("Unable to find mapping for " + prefix)
    val engine: MappingEngine = new MappingEngine(mapping.get.recordMapping, asJavaMap(dataSet.namespaces))
    val mappedRecord: String = engine.executeMapping(record.getXmlString())
    mappedRecord
  }
  // access control

  protected def getCollection = dataSetsCollection

  protected def getObjectIdField = "spec"
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

case class Mapping(recordMapping: String,
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

object MetadataFormat {

  def create(prefix: String, accessKeyRequired: Boolean = true): MetadataFormat = {
    import eu.delving.metadata.MetadataNamespace

    val ns: MetadataNamespace = MetadataNamespace.values().filter(ns => ns.getPrefix == prefix).headOption.getOrElse(
      throw new MetaRepoSystemException(String.format("Namespace prefix %s not recognized", prefix))
    )
    MetadataFormat(ns.getPrefix, ns.getSchema, ns.getUri, accessKeyRequired)
  }
}

case class Details(
                          name: String,
                          uploaded_records: Int = 0,
                          total_records: Int = 0,
                          deleted_records: Int = 0,
                          metadataFormat: MetadataFormat,
                          facts_bytes: Array[Byte],
                          errorMessage: Option[String] = Some("")
                          )

case class MetadataRecord(_id: ObjectId = new ObjectId,
                          metadata: scala.collection.mutable.Map[String, String], // this is the raw xml data string
                          modified: Date,
                          deleted: Boolean, // if the record has been deleted
                          localRecordKey: String, // content fingerprint
                          globalHash: String, // the hash of the raw content
                          hash: Map[String, String]) { //extends MetadataRecord {
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
    val xmlString: Option[String] = metadata.get(metadataPrefix)
    if (xmlString == None) throw new RecordNotFoundException("Unable to find record with source metadata prefix: %s".format(metadataPrefix))
    xmlString.get
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

case class RecordIdentifier(identifier: String, spec: String, dataSet: DataSet, rawId: String)