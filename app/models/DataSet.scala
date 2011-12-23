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

package models

import java.util.Date
import org.bson.types.ObjectId
import models.salatContext._
import com.mongodb.casbah.Imports._
import com.novus.salat._
import dao.SalatDAO
import cake.metaRepo.PmhVerbType.PmhVerb
import com.mongodb.{BasicDBObject, WriteConcern}
import java.io.File
import play.exceptions.ConfigurationException
import eu.delving.metadata.{Path, RecordMapping}
import xml.{Node, XML}
import cake.ComponentRegistry
import play.i18n.Messages
import eu.delving.sip.IndexDocument
import controllers.{MetadataAccessors, SolrServer, ModelImplicits}
import com.mongodb.casbah.{MongoCollection}
import _root_.util.Constants._

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
                   idxMappings: List[String] = List.empty[String], // the mapping(s) used at indexing time (for the moment, use only one)
                   idxFacets: List[String] = List.empty[String],
                   idxSortFields: List[String] = List.empty[String],
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
  
  def getIndexingMappingPrefix = idxMappings.headOption

  def hasHash(hash: String): Boolean = hashes.values.filter(h => h == hash).nonEmpty

  def hasDetails: Boolean = details != null

  def hasRecords: Boolean = connection(DataSet.getRecordsCollectionName(this)).count != 0

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

object DataSet extends SalatDAO[DataSet, ObjectId](collection = dataSetsCollection) with Pager[DataSet] with SolrServer with ModelImplicits {

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

    val stateData = dataSetsCollection.findOne(
      MongoDBObject("orgId" -> orgId, "spec" -> spec),
      MongoDBObject("state" -> 1, "details" -> 1)).getOrElse(return (100, 100))

    val details: MongoDBObject = stateData.get("details").asInstanceOf[DBObject]

    val totalRecords = details.getAsOrElse[Int]("total_records", 0)
    val indexingCount = details.getAsOrElse[Int]("indexing_count", 0)
    val invalidRecords = details.getAsOrElse[Int]("invalid_records", 0)

    if(stateData.getAs[DBObject]("state").get("name") == DataSetState.ENABLED.name) return (100, 100)

    (indexingCount, totalRecords - invalidRecords)
  }

  def findCollectionForIndexing() : Option[DataSet] = {
    val allDataSets: List[DataSet] = findByState(DataSetState.INDEXING).sort(MongoDBObject("name" -> 1)).toList
    if (allDataSets.length < 3) {
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

  def findAllForUser(userName: String, grantType: GrantType): List[DataSet] =
    Group.
            find(MongoDBObject("users" -> userName)).
            filter(g => g.grantType == grantType || g.grantType == GrantType.OWN).
            map(g => if(g.grantType == GrantType.OWN) DataSet.findAllByOrgId(g.orgId).toList else DataSet.find("_id" $in g.dataSets).toList).
            toList.flatten.distinct

  def canView(ds: DataSet, userName: String) = {
    Organization.isOwner(ds.orgId, userName) ||
    Group.count(MongoDBObject("dataSets" -> ds._id, "users" -> userName)) > 0 ||
    ds.visibility == Visibility.PUBLIC
  }

  def canEdit(ds: DataSet, userName: String) = {
    Organization.isOwner(ds.orgId, userName) || Group.count(MongoDBObject(
      "dataSets" -> ds._id,
      "users" -> userName,
      "grantType.value" -> GrantType.MODIFY.value)
    ) > 0
  }

  def findAllCanSee(orgId: String, userName: String): List[DataSet] = {
    if(Organization.isOwner(orgId, userName)) return DataSet.findAllByOrgId(orgId).toList
    val ids = Group.find(MongoDBObject("orgId" -> orgId, "users" -> userName)).map(_.dataSets).toList.flatten.distinct
    (DataSet.find(("_id" $in ids)) ++ DataSet.find(MongoDBObject("orgId" -> orgId, "visibility.value" -> Visibility.PUBLIC.value))).map(entry => (entry._id, entry)).toMap.values.toList
  }

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

  def updateInvalidRecords(dataSet: DataSet, prefix: String, invalidIndexes: List[Int]) {
    val updatedDetails = dataSet.details.copy(invalid_records = Some(invalidIndexes.size))
    val updatedDataSet = dataSet.copy(invalidRecords = dataSet.invalidRecords.updated(prefix, invalidIndexes), details = updatedDetails)
    DataSet.save(updatedDataSet)

    if(dataSet.hasRecords) {
      val collection = getRecordsCollection(dataSet)
      collection.update(MongoDBObject(), $addToSet ("validOutputFormats" -> prefix), false, true)
      collection.update("transferIdx" $in (invalidIndexes), $pull("validOutputFormats" -> prefix), false, true)
    }
  }

  def unlock(dataSet: DataSet) {
    update(MongoDBObject("_id" -> dataSet._id), $unset("lockedBy"))
  }

  def addHash(dataSet: DataSet, key: String, hash: String) {
    update(MongoDBObject("_id" -> dataSet._id), MongoDBObject("$set" -> MongoDBObject(("hashes." + key) -> hash)))
  }

  def delete(dataSet: DataSet) {
    if(connection.getCollectionNames().contains(getRecordsCollectionName(dataSet))) {
      connection(getRecordsCollectionName(dataSet)).rename(getRecordsCollectionName(dataSet) + "_" + dataSet._id.toString)
    }
    update(MongoDBObject("_id" -> dataSet._id), $set ("deleted" -> true), false, false)
  }

  def getRecordsCollectionName(dataSet: DataSet): String = getRecordsCollectionName(dataSet.orgId, dataSet.spec)

  def getRecordsCollectionName(orgId: String, spec: String): String = "Records.%s_%s".format(orgId, spec)

  def getRecordsCollection(dataSet: DataSet): MongoCollection = connection(DataSet.getRecordsCollectionName(dataSet))

  // TODO should we cache the constructions of these objects?
  def getRecords(dataSet: DataSet): SalatDAO[MetadataRecord, ObjectId] with MDRCollection  = {
    val recordCollection: MongoCollection = connection(getRecordsCollectionName(dataSet))
    recordCollection.ensureIndex(MongoDBObject("localRecordKey" -> 1))
    recordCollection.ensureIndex(MongoDBObject("transferIdx" -> 1))
    object CollectionMDR extends SalatDAO[MetadataRecord, ObjectId](recordCollection) with MDRCollection
    CollectionMDR
  }

  /**
   * identifier = orgId:spec:localRecordKey
   *
   * this entails that orgIds, specs and localRecordKey-s never change
   */
  def getRecord(identifier: String, metadataFormat: String): Option[MetadataRecord] = {
    if(identifier.split(":").length != 3)
      throw new InvalidIdentifierException("Invalid record identifier %s, should be of the form orgId:spec:localIdentifier".format(identifier))
    val Array(orgId, spec, localRecordKey) = identifier.split(":")
    val ds: Option[DataSet] = findBySpecAndOrgId(spec, orgId)
    if(ds == None) return None
    val record: Option[MetadataRecord] = getRecords(ds.get).findOne(MongoDBObject("localRecordKey" -> localRecordKey))
    if(record == None) return None
    if (record.get.rawMetadata.contains(metadataFormat))
      record
    else {
      val mappedRecord = record.get
      val transformedDoc: DBObject = transFormXml(metadataFormat, ds.get, mappedRecord)

      Some(mappedRecord.copy(mappedMetadata = mappedRecord.mappedMetadata.updated(metadataFormat, transformedDoc)))
    }
  }

  @Deprecated
  def getStateBySpec(spec: String) = DataSet.findBySpec(spec).get.state

  def getStateBySpecAndOrgId(spec: String, orgId: String) = DataSet.findBySpecAndOrgId(spec, orgId).get.state

  def changeState(dataSet: DataSet, state: DataSetState): DataSet = {
    val dataSetLatest = DataSet.findBySpecAndOrgId(dataSet.spec, dataSet.orgId).get
    val mappings = dataSetLatest.mappings.transform((key, map) => map.copy(rec_indexed = 0))
    val updatedDataSet = dataSetLatest.copy(state = state, mappings = mappings)
    DataSet.save(updatedDataSet)
    updatedDataSet
  }

  def addIndexingState(dataSet: DataSet, mapping: String, facets: List[String], sortFields: List[String]) {
    DataSet.update(MongoDBObject("_id" -> dataSet._id), $addToSet("idxMappings" -> mapping) ++ $set("idxFacets" -> facets, "idxSortFields" -> sortFields))
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
                   invalid_records: Option[Int] = Some(0),
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
                          hubId: String,
                          rawMetadata: Map[String, String], // this is the raw xml data string
                          mappedMetadata: Map[String, DBObject] = Map.empty[String, DBObject], // this is the mapped xml data string only added after transformation, and it's a DBObject because Salat won't let us use an inner Map[String, List[String]]
                          modified: Date = new Date(),
                          validOutputFormats: List[String] = List.empty[String], // valid formats this records can be mapped to
                          deleted: Boolean = false, // if the record has been deleted
                          transferIdx: Option[Int] = None, // 0-based index for the transfer order
                          localRecordKey: String, // the unique element value
                          links: List[EmbeddedLink] = List.empty[EmbeddedLink],
                          globalHash: String, // the hash of the raw content
                          hash: Map[String, String] // the hash for each field, for duplicate detection
                         ) {

  def getUri(orgId: String, spec: String) = "http://%s/%s/object/%s/%s".format(getNode, orgId, spec, localRecordKey)

  def getXmlString(metadataPrefix: String = "raw"): String = {
    if (rawMetadata.contains(metadataPrefix)) {
      rawMetadata.get(metadataPrefix).get
    }
    else if (mappedMetadata.contains(metadataPrefix)) {
      import scala.collection.JavaConversions._
      val indexDocument: MongoDBObject = mappedMetadata.get(metadataPrefix).get
      indexDocument.entrySet().foldLeft("")(
        (output, indexDoc) => {
          val unMungedKey = indexDoc.getKey.replaceFirst("_", ":")
          output + indexDoc.getValue.asInstanceOf[List[String]].map(value => {
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

  def getDefaultAccessor = {
    val (prefix, map) = mappedMetadata.head
    new MultiValueMapMetadataAccessors(hubId, map)
  }
  
  def getAccessor(prefix: String) = {
    if(!mappedMetadata.contains(prefix)) new MultiValueMapMetadataAccessors(hubId, MongoDBObject())
    val map = mappedMetadata(prefix)
    new MultiValueMapMetadataAccessors(hubId, map)
  }

}


object MetadataRecord {

  def getMDR(hubId: String): Option[MetadataRecord] = {
    val Array(orgId, spec, recordId) = hubId.split("_")
    val collectionName = DataSet.getRecordsCollectionName(orgId, spec)
    connection(collectionName).findOne(MongoDBObject(MDR_HUB_ID -> hubId)) match {
      case Some(dbo) => Some(grater[MetadataRecord].asObject(dbo))
      case None => None
    }
  }

}

class MultiValueMapMetadataAccessors(hubId: String, dbo: MongoDBObject) extends MetadataAccessors {
  protected def assign(key: String) = {
    dbo.get(key) match {
      case Some(v) => v.asInstanceOf[BasicDBList].toList.head.toString
      case None => ""
    }
  }

  override def getHubId = hubId
  override def getRecordType = _root_.util.Constants.MDR
}

trait MDRCollection {
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

class InvalidIdentifierException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}