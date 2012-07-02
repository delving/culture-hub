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
import org.bson.types.ObjectId
import models.mongoContext._
import com.mongodb.casbah.Imports._
import com.novus.salat.dao._
import xml.{Node, XML}
import exceptions.MetaRepoSystemException
import play.api.i18n.Messages
import core.HubServices
import eu.delving.metadata.RecMapping
import play.api.Play
import play.api.Play.current
import java.net.URL
import core.Constants._
import core.storage.{BaseXCollection}
import models.statistics.DataSetStatistics

/**
 * DataSet model
 * The unique ID for this model is the mongo _id. IF YOU WANT TO USE THE SPEC, ALWAYS ALSO USE THE ORG_ID. The spec alone does not provide for unicity accross organizations!
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since 7/8/11 8:12 AM  
 */

case class DataSet(

                   // basics
                   _id: ObjectId = new ObjectId,
                   spec: String,
                   orgId: String,
                   userName: String, // creator

                   // state
                   state: DataSetState,
                   errorMessage: Option[String] = None,

                   // not used
                   visibility: Visibility = Visibility.PUBLIC, // fixed to public. We'll see in the future whether this is still necessary to have or should be removed.
                   deleted: Boolean = false, // fixed to false, not used. We simply delete a set. TODO decide whether we remove this.

                   details: Details,

                   // sip-creator integration
                   lockedBy: Option[String] = None,
                   hashes: Map[String, String] = Map.empty[String, String],
                   hints: Array[Byte] = Array.empty[Byte],

                   // mapping
                   namespaces: Map[String, String] = Map.empty[String, String], // this map contains all namespaces of the source format, and is necessary for mapping
                   mappings: Map[String, Mapping] = Map.empty[String, Mapping],
                   invalidRecords: Map[String, List[Int]] = Map.empty[String, List[Int]],

                   // harvesting
                   formatAccessControl: Map[String, FormatAccessControl], // access control for each format of this DataSet (for OAI-PMH)

                   // indexing
                   idxMappings: List[String] = List.empty[String], // the mapping(s) used at indexing time (for the moment, use only one)
                   idxFacets: List[String] = List.empty[String], // the facet fields selected for indexing, at the moment derived from configuration
                   idxSortFields: List[String] = List.empty[String], // the sort fields selected for indexing, at the moment derived from configuration
                   ) {

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

  def getAllMappingSchemas = mappings.map(mapping => mapping._2.format).toList

  def getPublishableMappingSchemas = getAllMappingSchemas.
    filter(format => formatAccessControl.get(format.prefix).isDefined).
    filter(format => formatAccessControl(format.prefix).isPublicAccess || formatAccessControl(format.prefix).isProtectedAccess).
    toList

  def getVisibleMetadataSchemas(accessKey: Option[String] = None): List[RecordDefinition] = {
    getAllMappingSchemas.
      filterNot(format => formatAccessControl.get(format.prefix).isEmpty).
      filter(format => formatAccessControl(format.prefix).hasAccess(accessKey)
    )
  }

  def hasHash(hash: String): Boolean = hashes.values.filter(h => h == hash).nonEmpty

  def hasDetails: Boolean = details != null

  def hasRecords: Boolean = {
    DataSet.storage.openCollection(this.orgId, this.spec).isDefined && DataSet.getRecordCount(this) != 0
  }

}

object DataSet extends SalatDAO[DataSet, ObjectId](collection = dataSetsCollection) with Pager[DataSet] {

  lazy val storage = HubServices.basexStorage

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

    if(stateData.getAs[DBObject]("state").get("name") == DataSetState.ENABLED.name) return (100, 100)

    (processingCount, totalRecords)
  }

  def findCollectionForIndexing() : Option[DataSet] = {
    val allDataSets: List[DataSet] = findByState(DataSetState.PROCESSING).sort(MongoDBObject("name" -> 1)).toList
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
    (DataSet.find(("_id" $in ids)) ++ DataSet.find(MongoDBObject("orgId" -> orgId))).filterNot(_.deleted).toList
  }

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

  // workaround for salat not working as it should
  def getInvalidRecords(dataSet: DataSet): Map[String, Set[Int]] = {
    import scala.collection.JavaConverters._
    dataSetsCollection.findOne(MongoDBObject("_id" -> dataSet._id), MongoDBObject("invalidRecords" -> 1)).map {
      ds => {
        val map = ds.getAs[DBObject]("invalidRecords").get
        map.map(valid => {
            val key = valid._1.toString
            val value: Set[Int] = valid._2.asInstanceOf[com.mongodb.BasicDBList].asScala.map(index => index match {
              case int if int.isInstanceOf[Int] => int.asInstanceOf[Int]
              case double if double.isInstanceOf[java.lang.Double] => double.asInstanceOf[java.lang.Double].intValue()
            }).toSet
            (key, value)
          }).toMap[String, Set[Int]]
      }
    }.getOrElse {
      Map.empty
    }
  }


  // ~~~ update. make sure you always work with the latest version from mongo after an update - operations are not atomic

  def updateById(id: ObjectId, dataSet: DataSet) {
    DataSet.update(MongoDBObject("_id" -> dataSet._id), DataSet._grater.asDBObject(dataSet))
  }

  def upsertById(id: ObjectId, dataSet: DataSet) {
    DataSet.update(MongoDBObject("_id" -> dataSet._id), DataSet._grater.asDBObject(dataSet), true)
  }

  def updateInvalidRecords(dataSet: DataSet, prefix: String, invalidIndexes: List[Int]) {
    val updatedDetails = dataSet.details.copy(invalidRecordCount = (dataSet.details.invalidRecordCount + (prefix -> invalidIndexes.size)))
    val updatedDataSet = dataSet.copy(invalidRecords = dataSet.invalidRecords.updated(prefix, invalidIndexes), details = updatedDetails)
    DataSet.save(updatedDataSet)
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
    MetadataCache.get(dataSet.orgId, dataSet.spec, ITEM_TYPE_MDR).removeAll()
    storage.deleteCollection(BaseXCollection(dataSet.orgId, dataSet.spec))
    remove(dataSet)
  }

  // ~~~ record handling

  def getRecordCount(dataSet: DataSet): Long = storage.count(BaseXCollection(dataSet.orgId, dataSet.spec))

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

  def updateRecordCount(dataSet: DataSet, count: Long) {
    DataSet.update(MongoDBObject("_id" -> dataSet._id), MongoDBObject("$set" -> MongoDBObject("details.total_records" -> count)))
  }


  def invalidateHashes(dataSet: DataSet) {
    DataSet.update(MongoDBObject("_id" -> dataSet._id), $unset ("hashes"))
  }

  // ~~~ OAI-PMH

  def getAllVisibleMetadataFormats(orgId: String, accessKey: Option[String]): List[RecordDefinition] = {
    val metadataFormats = findAll(orgId).flatMap {
      ds => ds.getVisibleMetadataSchemas(accessKey)
    }
    metadataFormats.toList.distinct
  }

  def getMetadataFormats(spec: String, orgId: String, accessKey: Option[String]): List[RecordDefinition] = {
    findBySpecAndOrgId(spec, orgId) match {
      case Some(ds) => ds.getVisibleMetadataSchemas(accessKey)
      case None => List[RecordDefinition]()
    }
  }

  // statistics

  def getMostRecentDataSetStatistics = {
    DataSetStatistics.find(MongoDBObject()).$orderby(MongoDBObject("_id" -> -1)).limit(1).toList.headOption
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
  val ENABLED = DataSetState("enabled")
  val DISABLED = DataSetState("disabled")
  val ERROR = DataSetState("error")
  val NOTFOUND = DataSetState("notfound")
  def withName(name: String): Option[DataSetState] = if(valid(name)) Some(DataSetState(name)) else None
  def valid(name: String) = values.contains(DataSetState(name))
  val values = List(INCOMPLETE, PARSING, UPLOADED, QUEUED, PROCESSING, ENABLED, DISABLED, ERROR, NOTFOUND)
}

case class Mapping(recordMapping: Option[String] = None, format: RecordDefinition)

case class Details(name: String,
                   total_records: Long = 0,
                   indexing_count: Long = 0,
                   invalidRecordCount: Map[String, Long] = Map.empty,
                   facts: BasicDBObject = new BasicDBObject()
                  ) {

  def getFactsAsText: String = {
    val builder = new StringBuilder
    facts foreach {
      fact => builder.append(fact._1).append("=").append(fact._2).append("\n")
    }
    builder.toString()
  }

}