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

import extensions.ConfigurationException
import java.util.Date
import org.bson.types.ObjectId
import models.mongoContext._
import com.mongodb.casbah.Imports._
import com.novus.salat.dao._
import com.mongodb.{BasicDBObject, WriteConcern}
import xml.{Node, XML}
import com.mongodb.casbah.MongoCollection
import exceptions.MetaRepoSystemException
import play.api.i18n.Messages
import core.HubServices
import eu.delving.metadata.RecMapping
import play.api.Play
import play.api.Play.current
import java.net.URL
import core.Constants._
import scala.Predef._
import core.storage.BaseXStorage

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
                   userName: String,
                   orgId: Predef.String,
                   lockedBy: Option[String] = None,
                   description: Option[String] = Some(""),
                   state: DataSetState,
                   errorMessage: Option[String] = None,
                   visibility: Visibility,
                   deleted: Boolean = false,
                   details: Details,
                   lastUploaded: Date,
                   hashes: Map[String, String] = Map.empty[String, String],
                   namespaces: Map[String, String] = Map.empty[String, String], // FIXME: this map makes no sense here since the namespaces depend on the format in which a DataSet is rendered.
                   mappings: Map[String, Mapping] = Map.empty[String, Mapping],
                   formatAccessControl: Map[String, FormatAccessControl], // access control for each format of this DataSet (for OAI-PMH)
                   idxMappings: List[String] = List.empty[String], // the mapping(s) used at indexing time (for the moment, use only one)
                   idxFacets: List[String] = List.empty[String],
                   idxSortFields: List[String] = List.empty[String],
                   hints: Array[Byte] = Array.empty[Byte],
                   invalidRecords: Map[String, List[Int]] = Map.empty[String, List[Int]]) {

  // ~~~ accessors

  val name = spec

  def getCreator: HubUser = HubUser.findByUsername(userName).get // orElse we are in trouble

  def getLockedBy: Option[HubUser] = if(lockedBy == None) None else HubUser.findByUsername(lockedBy.get)

  def getFacts: Map[String, String] = {
    val initialFacts = (DataSet.factDefinitionList.map(factDef => (factDef.name, ""))).toMap[String, String]
    val storedFacts = (for (fact <- details.facts) yield (fact._1, fact._2.toString)).toMap[String, String]
    initialFacts ++ storedFacts
  }
  
  def getIndexingMappingPrefix = idxMappings.headOption

  /** all mapping formats **/
  def getAllMappingFormats = mappings.map(mapping => mapping._2.format).toList

  def getPublishableMappingFormats = getAllMappingFormats.
    filter(format => formatAccessControl.get(format.prefix).isDefined).
    filter(format => formatAccessControl(format.prefix).isPublicAccess || formatAccessControl(format.prefix).isProtectedAccess).
    toList

  /** all metadata formats, including raw **/
  def getAllMetadataFormats = details.metadataFormat :: getAllMappingFormats

  def getVisibleMetadataFormats(accessKey: Option[String] = None): List[RecordDefinition] = {
    getAllMetadataFormats.
      filterNot(format => formatAccessControl.get(format.prefix).isEmpty).
      filter(format => formatAccessControl(format.prefix).hasAccess(accessKey)
    )
  }

  def hasHash(hash: String): Boolean = hashes.values.filter(h => h == hash).nonEmpty

  def hasDetails: Boolean = details != null

  def hasRecords: Boolean = {
    BaseXStorage.openCollection(this.orgId, this.spec).isDefined && DataSet.getRecordCount(this) != 0
  }

}

object DataSet extends SalatDAO[DataSet, ObjectId](collection = dataSetsCollection) with Pager[DataSet] {

  lazy val factDefinitionList = parseFactDefinitionList

  def getFactDefinitionResource: URL = {
    val r = Play.resource(("definitions/global/fact-definition-list.xml"))
    if (!r.isDefined) throw ConfigurationException("Fact definition configuration file not found!")
    r.get
  }

  private def parseFactDefinitionList: Seq[FactDefinition] = {
    val xml = XML.load(getFactDefinitionResource)
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

  def getState(orgId: String, spec: String): DataSetState = {

    val stateData = dataSetsCollection.findOne(
      MongoDBObject("orgId" -> orgId, "spec" -> spec),
      MongoDBObject("state" -> 1)
    ).getOrElse(return DataSetState.NOTFOUND)

    val name = stateData.getAs[DBObject]("state").get("name").toString

    DataSetState(name)
  }

  def getProcessingState(orgId: String, spec: String): (Long, Long) = {

    val stateData = dataSetsCollection.findOne(
      MongoDBObject("orgId" -> orgId, "spec" -> spec),
      MongoDBObject("state" -> 1, "details" -> 1)
    ).getOrElse(return (0, 0))

    val details: MongoDBObject = stateData.get("details").asInstanceOf[DBObject]

    val totalRecords = details.getAsOrElse[Long]("total_records", 0)
    // this one is unboxed as Int because when we write it via $set it doesn't get written as a NumberLong...
    val processingCount = details.getAsOrElse[Long]("indexing_count", 0)
    val invalidRecords = details.getAsOrElse[Int]("invalid_records", 0)

    if(stateData.getAs[DBObject]("state").get("name") == DataSetState.ENABLED.name) return (100, 100)

    (processingCount, totalRecords - invalidRecords)
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

  // ~~~ finders

  def findBySpecAndOrgId(spec: String, orgId: String): Option[DataSet] = findOne(MongoDBObject("spec" -> spec, "orgId" -> orgId, "deleted" -> false))

  def findByState(state: DataSetState) = {
    DataSet.find(MongoDBObject("state.name" -> state.name, "deleted" -> false))
  }

  def findAll(orgId: String): List[DataSet] = find(MongoDBObject("deleted" -> false)).sort(MongoDBObject("name" -> 1)).toList


  def findAllForUser(userName: String, orgIds: List[String], grantType: GrantType): List[DataSet] = {
    val groupDataSets = Group.
                              find(MongoDBObject("users" -> userName)).
                              filter(g => g.grantType == grantType.key).
                              map(g => DataSet.find("_id" $in g.dataSets).toList).
                              toList.flatten
    val adminDataSets = orgIds.filter(orgId => HubServices.organizationService.isAdmin(orgId, userName)).map(orgId => DataSet.findAllByOrgId(orgId)).toList.flatten

    (groupDataSets ++ adminDataSets).distinct
  }

  def findAllCanSee(orgId: String, userName: String): List[DataSet] = {
    if(HubServices.organizationService.isAdmin(orgId, userName)) return DataSet.findAllByOrgId(orgId).toList
    val ids = Group.find(MongoDBObject("orgId" -> orgId, "users" -> userName)).map(_.dataSets).toList.flatten.distinct
    (DataSet.find(("_id" $in ids)) ++ DataSet.find(MongoDBObject("orgId" -> orgId, "visibility.value" -> Visibility.PUBLIC.value))).map(entry => (entry._id, entry)).toMap.values.toList
  }

  // FIXME this one makes no sense, since findAllCanSee only returns public datasets anyway
  // i.e. re-think & streamline the visibility concept for DataSets
  def findAllVisible(orgId: String, userName: String, organizations: String) = findAllCanSee(orgId, userName).filter(ds =>
    ds.visibility == Visibility.PUBLIC ||
      (
        ds.visibility == Visibility.PRIVATE &&
        organizations != null && organizations.split(",").contains(orgId)
      )
    ).toList

  def findAllByOrgId(orgId: String) = DataSet.find(MongoDBObject("orgId" -> orgId, "deleted" -> false))

  // ~~~ access control

  def canView(ds: DataSet, userName: String) = {
    HubServices.organizationService.isAdmin(ds.orgId, userName) ||
    Group.count(MongoDBObject("dataSets" -> ds._id, "users" -> userName)) > 0 ||
    ds.visibility == Visibility.PUBLIC
  }

  def canEdit(ds: DataSet, userName: String) = {
    HubServices.organizationService.isAdmin(ds.orgId, userName) || Group.count(MongoDBObject(
      "dataSets" -> ds._id,
      "users" -> userName,
      "grantType" -> GrantType.MODIFY.key)
    ) > 0
  }


  // ~~~ update. make sure you always work with the latest version from mongo after an update - operations are not atomic

  def updateById(id: ObjectId, dataSet: DataSet) {
    update(MongoDBObject("_id" -> dataSet._id), dataSet, false, false, new WriteConcern())
  }

  def upsertById(id: ObjectId, dataSet: DataSet) {
    update(MongoDBObject("_id" -> dataSet._id), dataSet, true, false, new WriteConcern())
  }

  def updateInvalidRecords(dataSet: DataSet, prefix: String, invalidIndexes: List[Int]) {
    val updatedDetails = dataSet.details.copy(invalid_records = Some(invalidIndexes.size))
    val updatedDataSet = dataSet.copy(invalidRecords = dataSet.invalidRecords.updated(prefix, invalidIndexes), details = updatedDetails)
    DataSet.save(updatedDataSet)

    if(dataSet.hasRecords) {
      // TODO do this via the XQuery update facility
      // TODO FIXME
    }
  }

  def updateMapping(dataSet: DataSet, mapping: RecMapping): DataSet = {
    val ns: Option[RecordDefinition] = RecordDefinition.recordDefinitions.filter(rd => rd.prefix == mapping.getPrefix).headOption
    if (ns == None) {
      throw new MetaRepoSystemException(String.format("Namespace prefix %s not recognized", mapping.getPrefix))
    }
    
    // if we already have a mapping, update it but keep the format access control settings
    val updatedMapping = dataSet.mappings.get(mapping.getPrefix) match {
      case Some(existingMapping) =>
        existingMapping.copy(
          format = existingMapping.format.copy(roles = ns.get.roles),
          recordMapping = Some(mapping.toString)
        )
      case None =>
        Mapping(
          recordMapping = Some(mapping.toString),
          format = RecordDefinition(
            ns.get.prefix,
            ns.get.schema,
            ns.get.namespace,
            ns.get.allNamespaces,
            ns.get.roles,
            ns.get.isFlat
          )
        )
    }
    val updatedDataSet = dataSet.copy(mappings = dataSet.mappings.updated(mapping.getPrefix, updatedMapping))
    DataSet.updateById(dataSet._id, updatedDataSet)
    updatedDataSet
  }

  def updateNamespaces(spec: String, namespaces: Map[String, String]) {
    update(MongoDBObject("spec" -> spec), $set ("namespaces" -> namespaces.asDBObject))
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

  // ~~~ record handling

  def getRecordsCollectionName(dataSet: DataSet): String = getRecordsCollectionName(dataSet.orgId, dataSet.spec)

  def getRecordsCollectionName(orgId: String, spec: String): String = "Records.%s_%s".format(orgId, spec)

  def getRecordsCollection(dataSet: DataSet): MongoCollection = connection(DataSet.getRecordsCollectionName(dataSet))

  def getRecordCount(dataSet: DataSet): Long = BaseXStorage.count(core.storage.Collection(dataSet.orgId, dataSet.spec))

  // ~~~ indexing control

  def updateStateAndProcessingCount(dataSet: DataSet, state: DataSetState, errorMessage: Option[String] = None): DataSet = {
    val dataSetLatest = DataSet.findBySpecAndOrgId(dataSet.spec, dataSet.orgId).get
    val updatedDataSet = dataSetLatest.copy(state = state, errorMessage = errorMessage)
    DataSet.save(updatedDataSet)
    updatedDataSet
  }

  def updateState(dataSet: DataSet, state: DataSetState, errorMessage: Option[String] = None) {
    if(errorMessage.isDefined) {
      update(MongoDBObject("_id" -> dataSet._id), $set("state.name" -> state.name, "errorMessage" -> errorMessage.get))
    } else {
      update(MongoDBObject("_id" -> dataSet._id), $set("state.name" -> state.name) ++ $unset("errorMessage"))
    }
  }

  def updateIndexingControlState(dataSet: DataSet, mapping: String, facets: List[String], sortFields: List[String]) {
    DataSet.update(MongoDBObject("_id" -> dataSet._id), $addToSet("idxMappings" -> mapping) ++ $set("idxFacets" -> facets, "idxSortFields" -> sortFields))
  }

  def updateIndexingCount(dataSet: DataSet, count: Long) {
    DataSet.update(MongoDBObject("_id" -> dataSet._id), MongoDBObject("$set" -> MongoDBObject("details.indexing_count" -> count)))
  }

  def invalidateHashes(dataSet: DataSet) {
    DataSet.update(MongoDBObject("_id" -> dataSet._id), $unset ("hashes"))
  }

  // ~~~ OAI-PMH

  def getAllVisibleMetadataFormats(orgId: String, accessKey: Option[String]): List[RecordDefinition] = {
    val metadataFormats = findAll(orgId).flatMap {
      ds => ds.getVisibleMetadataFormats(accessKey)
    }
    metadataFormats.toList.distinct
  }

  def getMetadataFormats(spec: String, orgId: String, accessKey: Option[String]): List[RecordDefinition] = {
    findBySpecAndOrgId(spec, orgId) match {
      case Some(ds) => ds.getVisibleMetadataFormats(accessKey)
      case None => List[RecordDefinition]()
    }
  }

}

case class FactDefinition(name: String, prompt: String, tooltip: String, automatic: Boolean = false, options: Seq[String]) {
  def hasOptions = !options.isEmpty

  val opts = options.map(opt => (opt, opt))
}

case class DataSetState(name: String) {

  def description = Messages("dataSetState." + name.toLowerCase)
}

object DataSetState {
  val INCOMPLETE = DataSetState("incomplete")
  val PARSING = DataSetState("parsing")
  val UPLOADED = DataSetState("uploaded")
  val QUEUED = DataSetState("queued")
  val PROCESSING = DataSetState("processing")
  val INDEXING = DataSetState("indexing")
  val ENABLED = DataSetState("enabled")
  val DISABLED = DataSetState("disabled")
  val ERROR = DataSetState("error")
  val NOTFOUND = DataSetState("notfound")
  def withName(name: String): Option[DataSetState] = if(valid(name)) Some(DataSetState(name)) else None
  def valid(name: String) = values.contains(DataSetState(name))
  val values = List(INCOMPLETE, UPLOADED, QUEUED, INDEXING, DISABLED, ERROR, NOTFOUND)
}

case class Mapping(recordMapping: Option[String] = None,
                   format: RecordDefinition,
                   errorMessage: Option[String] = Some(""),
                   indexed: Boolean = false)

case class Details(name: String,
                   uploaded_records: Long = 0,
                   total_records: Long = 0,
                   deleted_records: Long = 0,
                   indexing_count: Long = 0,
                   invalid_records: Option[Int] = Some(0),
                   metadataFormat: RecordDefinition,
                   facts: BasicDBObject = new BasicDBObject(),
                   errorMessage: Option[String] = Some("")
                  ) {

  def getFactsAsText: String = {
    val builder = new StringBuilder
    facts foreach {
      fact => builder.append(fact._1).append("=").append(fact._2).append("\n")
    }
    builder.toString()
  }


}