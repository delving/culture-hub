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

import core.access.{ Resource, ResourceType }
import models.HubMongoContext._
import com.mongodb.casbah.Imports._
import exceptions.MetaRepoSystemException
import core.{ OrganizationService, DomainServiceLocator, HubModule, HubServices }
import eu.delving.metadata.RecMapping
import models.statistics.DataSetStatistics
import core.ItemType
import core.collection.{ OrganizationCollection, OrganizationCollectionMetadata, Harvestable }
import plugins.DataSetPlugin
import java.util.Date
import util.OrganizationConfigurationHandler
import eu.delving.schema.SchemaVersion
import java.io.StringReader
import core.mapping.MappingService
import com.escalatesoft.subcut.inject.{ BindingModule, Injectable }
import core.SchemaService
import play.api.Play
import com.novus.salat.dao.SalatDAO
import com.mongodb.casbah.gridfs.GridFSDBFile
import actors.DataSetEvent

/**
 * DataSet model
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
    description: Option[String] = None,

    // state
    state: DataSetState,
    errorMessage: Option[String] = None,
    processingInstanceIdentifier: Option[String] = None,

    // not used, fixed to public. We'll see in the future whether this is still necessary to have or should be removed.
    visibility: Visibility = Visibility.PUBLIC,

    // fixed to false, not used. We simply delete a set. TODO decide whether we remove this.
    deleted: Boolean = false,

    details: Details,

    // ~~~ sip-creator integration
    lockedBy: Option[String] = None,
    hashes: Map[String, String] = Map.empty[String, String],
    hints: Array[Byte] = Array.empty[Byte],

    // ~~~ mapping

    // this map contains all namespaces of the source format, and is necessary for mapping
    namespaces: Map[String, String] = Map.empty[String, String],

    mappings: Map[String, Mapping] = Map.empty[String, Mapping],

    // for each prefix, indexes of the records that are not valid for that schema
    invalidRecords: Map[String, List[Int]] = Map.empty[String, List[Int]],

    // ~~~ harvesting
    formatAccessControl: Map[String, FormatAccessControl], // access control for each format of this DataSet (for OAI-PMH)

    // ~~~ indexing

    // the mapping(s) used at indexing time (for the moment, use only one)
    idxMappings: List[String] = List.empty[String],

    // TODO not in use anymore, we read the configuration directly. revive if necessary.

    // the facet fields selected for indexing, at the moment derived from configuration
    idxFacets: List[String] = List.empty[String],

    // the sort fields selected for indexing, at the moment derived from configuration
    idxSortFields: List[String] = List.empty[String]) extends OrganizationCollection with OrganizationCollectionMetadata with Harvestable with Resource {

  implicit val configuration = OrganizationConfigurationHandler.getByOrgId(orgId)
  val organizationServiceLocator = HubModule.inject[DomainServiceLocator[OrganizationService]](name = None)

  val itemType: ItemType = DataSetPlugin.ITEM_TYPE

  // ~~~ accessors

  def getName: String = details.name

  def getTotalRecords: Long = details.total_records

  def getDescription: Option[String] = description

  def getOwner: String = orgId

  def getCreator: String = userName

  def getLockedBy: Option[HubUser] = lockedBy.flatMap(u => HubUser.dao.findByUsername(u))

  def getStoredFacts: Map[String, String] = {
    (for (fact <- details.facts) yield (fact._1, fact._2.toString)).toMap[String, String]
  }

  def getAllMappingSchemas: Seq[SchemaVersion] = mappings.map(mapping =>
    new SchemaVersion(mapping._2.schemaPrefix, mapping._2.schemaVersion)
  ).toSeq.distinct

  def getPublishableMappingSchemas = getAllMappingSchemas.filter(schema =>
    formatAccessControl.get(schema.getPrefix).isDefined
  ).filter(schema =>
    formatAccessControl(schema.getPrefix).isPublicAccess
      || formatAccessControl(schema.getPrefix).isProtectedAccess
  ).toList

  def getVisibleMetadataSchemas(accessKey: Option[String] = None): Seq[RecordDefinition] = {
    getAllMappingSchemas.
      filterNot(schema => formatAccessControl.get(schema.getPrefix).isEmpty).
      filter(schema => formatAccessControl(schema.getPrefix).hasAccess(accessKey)).
      flatMap(schema => RecordDefinition.getRecordDefinition(schema))
  }

  def hasHash(hash: String): Boolean = hashes.values.filter(h => h == hash).nonEmpty

  def hasDetails: Boolean = details != null

  def hasRecords: Boolean = {
    HubServices.basexStorages.getResource(configuration).openCollection(this, None)
      .isDefined && DataSet.dao(orgId).getSourceRecordCount(this) != 0
  }

  // ~~~ access rights

  def getResourceKey: String = spec

  def getResourceType = DataSet.RESOURCE_TYPE

  def editors: Seq[String] = Group.dao.findUsersWithAccess(
    orgId, DataSetPlugin.ROLE_DATASET_EDITOR, this
  )

  lazy val administrators: Seq[String] = (Group.dao.findResourceAdministrators(orgId, DataSet.RESOURCE_TYPE)
    ++ organizationServiceLocator.byDomain.listAdmins(orgId)
  ).distinct

  // ~~~ harvesting

  def getRecords(
    metadataFormat: String, position: Int, limit: Int, from: Option[Date], until: Option[Date]): (List[MetadataItem], Long) = {
    val cache = MetadataCache.get(orgId, spec, DataSetPlugin.ITEM_TYPE)
    val records = cache.list(position, Some(limit), from, until).filter(_.xml.contains(metadataFormat))
    val totalSize = cache.count()
    (records, totalSize)
  }

  def getNamespaces: Map[String, String] = namespaces

  // ~~~ indexing

  def getIndexingMappingPrefix = idxMappings.headOption

  // ~~~ collection information

  def getLanguage: String = details.facts.getAsOrElse[String]("language", "")

  def getCountry: String = details.facts.getAsOrElse[String]("country", "")

  def getProvider: String = details.facts.getAsOrElse[String]("provider", "")

  def getProviderUri: String = details.facts.getAsOrElse[String]("providerUri", "")

  def getDataProvider: String = details.facts.getAsOrElse[String]("dataProvider", "")

  def getDataProviderUri: String = details.facts.getAsOrElse[String]("dataProviderUri", "")

  def getRights: String = details.facts.getAsOrElse[String]("rights", "")

  def getType: String = details.facts.getAsOrElse[String]("type", "")

  override def canEqual(that: Any): Boolean = that.isInstanceOf[DataSet]

  override def hashCode(): Int = (orgId + spec).hashCode

  override def equals(other: Any): Boolean = canEqual(other) && {
    val ds = other.asInstanceOf[DataSet]
    ds.orgId == orgId && ds.spec == spec && ds.userName == userName && ds._id == _id
  }
}

object DataSet extends MultiModel[DataSet, DataSetDAO] {

  protected def connectionName: String = "Datasets"

  protected def initIndexes(collection: MongoCollection) {}

  protected def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: OrganizationConfiguration): DataSetDAO = new DataSetDAO(collection)(configuration, HubModule)

  val RESOURCE_TYPE = ResourceType("dataSet")

}

class DataSetDAO(collection: MongoCollection)(implicit val configuration: OrganizationConfiguration, val bindingModule: BindingModule)
    extends SalatDAO[DataSet, ObjectId](collection) with Pager[DataSet] with Injectable {

  val organizationServiceLocator = inject[DomainServiceLocator[OrganizationService]]

  val schemaService = inject[SchemaService]

  def getState(orgId: String, spec: String): DataSetState = {

    val stateData = collection.findOne(
      MongoDBObject("orgId" -> orgId, "spec" -> spec),
      MongoDBObject("state" -> 1)
    ).getOrElse(return DataSetState.NOTFOUND)

    val name = stateData.getAs[DBObject]("state").get("name").toString

    DataSetState(name)
  }

  def findCollectionForProcessing: Option[DataSet] = {
    val allProcessingSets: List[DataSet] = findByState(DataSetState.PROCESSING).sort(MongoDBObject("spec" -> 1)).toList
    val instanceIdentifier = Play.current.configuration.getString("cultureHub.instanceIdentifier").getOrElse("default")
    val localProcessingSets = allProcessingSets.filter(_.processingInstanceIdentifier == Some(instanceIdentifier))

    // only process one set at a time, since we now make optimal use of CPU cores
    if (localProcessingSets.length < 1) {
      val allQueuedForProcessing = findByState(DataSetState.QUEUED).sort(MongoDBObject("spec" -> 1)).toList
      val availableQueuedProcessing = allQueuedForProcessing.filterNot(set => set.processingInstanceIdentifier.isDefined && set.processingInstanceIdentifier.get != instanceIdentifier)
      availableQueuedProcessing.headOption
    } else {
      None
    }
  }

  // ~~~ finders

  def findBySpecAndOrgId(spec: String, orgId: String): Option[DataSet] = findOne(MongoDBObject("spec" -> spec, "orgId" -> orgId, "deleted" -> false))

  def findByState(states: DataSetState*) = {
    find("state.name" $in (states.map(_.name)) ++ MongoDBObject("deleted" -> false))
  }

  def findAll(): List[DataSet] = find(MongoDBObject()).sort(MongoDBObject("name" -> 1)).toList

  // ~~~ access control

  // TODO generify, move to Group
  def findAllForUser(userName: String, orgId: String, role: Role)(implicit configuration: OrganizationConfiguration): Seq[DataSet] = {

    val userGroups: Seq[Group] = Group.dao.find(MongoDBObject("users" -> userName)).toSeq
    val userRoles: Seq[Role] = userGroups.map(group => Role.get(group.roleKey))

    val isResourceAdmin = userRoles.exists { userRole =>
      role.resourceType.isDefined && role.resourceType == userRole.resourceType && userRole.isResourceAdmin
    }
    val isAdmin = organizationServiceLocator.byDomain.isAdmin(orgId, userName)

    val groupDataSets: Seq[DataSet] = userGroups.filter(
      group => group.roleKey == role.key
    ).flatMap(g => g.resources).filter(resource =>
        resource.getResourceType == DataSet.RESOURCE_TYPE
      ).flatMap(dataSetResources =>
        findBySpecAndOrgId(dataSetResources.getResourceKey, orgId)
      ).toSeq

    val adminDataSets: Seq[DataSet] = if (isResourceAdmin || isAdmin) findAllByOrgId(orgId).toSeq else Seq.empty

    (groupDataSets ++ adminDataSets).distinct
  }

  def findAllByOrgId(orgId: String): Seq[DataSet] = find(MongoDBObject("orgId" -> orgId, "deleted" -> false)).$orderby(MongoDBObject("details.name" -> 1)).toSeq

  def canEdit(ds: DataSet, userName: String)(implicit configuration: OrganizationConfiguration) = {
    findAllForUser(userName, configuration.orgId, DataSetPlugin.ROLE_DATASET_EDITOR).contains(ds)
  }

  def canAdministrate(userName: String)(implicit configuration: OrganizationConfiguration) = {
    Group.dao.findResourceAdministrators(configuration.orgId, DataSet.RESOURCE_TYPE).contains(userName) ||
      organizationServiceLocator.byDomain.isAdmin(configuration.orgId, userName)
  }

  // workaround for salat not working as it should
  def getInvalidRecords(dataSet: DataSet): Map[String, Set[Int]] = {
    import scala.collection.JavaConverters._
    collection.findOne(MongoDBObject("_id" -> dataSet._id), MongoDBObject("invalidRecords" -> 1)).map {
      ds =>
        {
          val map = ds.getAs[DBObject]("invalidRecords").get
          map.map(valid => {
            val key = valid._1.toString
            val value: Set[Int] = valid._2.asInstanceOf[com.mongodb.BasicDBList].asScala.map(
              index =>
                index match {
                  case int if int.isInstanceOf[Int] => int.asInstanceOf[Int]
                  case double if double.isInstanceOf[java.lang.Double] =>
                    double.asInstanceOf[java.lang.Double].intValue()
                }
            ).toSet
            (key, value)
          }).toMap[String, Set[Int]]
        }
    }.getOrElse {
      Map.empty
    }
  }

  // ~~~ update. make sure you always work with the latest version from mongo after an update - operations are not atomic

  def updateById(id: ObjectId, dataSet: DataSet) {
    update(MongoDBObject("_id" -> dataSet._id), _grater.asDBObject(dataSet))
  }

  def updateInvalidRecords(dataSet: DataSet, prefix: String, invalidIndexes: List[Int]) {
    val updatedDetails = dataSet.details.copy(
      invalidRecordCount = (dataSet.details.invalidRecordCount + (prefix -> invalidIndexes.size))
    )
    val updatedDataSet = dataSet.copy(
      invalidRecords = dataSet.invalidRecords.updated(prefix, invalidIndexes),
      details = updatedDetails
    )
    save(updatedDataSet)
    // TODO fire off appropriate state change event
  }

  def updateMapping(dataSet: DataSet, mappingString: String)(implicit configuration: OrganizationConfiguration): DataSet = {
    val mapping = RecMapping.read(new StringReader(mappingString), MappingService.recDefModel(schemaService))
    val recordDefinition: Option[RecordDefinition] = RecordDefinition.getRecordDefinition(
      mapping.getPrefix,
      mapping.getSchemaVersion.getVersion
    )
    if (recordDefinition == None) {
      throw new MetaRepoSystemException(
        "RecordDefinition with prefix %s and version %s not recognized".format(
          mapping.getPrefix, mapping.getSchemaVersion.getVersion
        )
      )
    }

    // if we already have a mapping, update it but keep the format access control settings
    val updatedMapping = dataSet.mappings.values.find(
      m => m.schemaPrefix == mapping.getPrefix && m.schemaVersion == mapping.getSchemaVersion.getVersion
    ) match {
        case Some(existingMapping) =>
          existingMapping.copy(recordMapping = Some(mappingString))
        case None =>
          Mapping(
            recordMapping = Some(mappingString),
            schemaPrefix = mapping.getSchemaVersion.getPrefix,
            schemaVersion = mapping.getSchemaVersion.getVersion
          )
      }
    val updatedDataSet = dataSet.copy(mappings = dataSet.mappings.updated(mapping.getPrefix, updatedMapping))
    updateById(dataSet._id, updatedDataSet)
    updatedDataSet
    // TODO fire off appropriate state change event
  }

  def updateNamespaces(spec: String, namespaces: Map[String, String]) {
    update(MongoDBObject("spec" -> spec), $set("namespaces" -> namespaces.asDBObject))
  }

  def lock(dataSet: DataSet, userName: String) {
    update(MongoDBObject("_id" -> dataSet._id), $set("lockedBy" -> userName))
    DataSetEvent ! DataSetEvent.Locked(dataSet.orgId, dataSet.spec, userName)
  }

  def unlock(dataSet: DataSet, userName: String) {
    update(MongoDBObject("_id" -> dataSet._id), $unset("lockedBy"))
    DataSetEvent ! DataSetEvent.Unlocked(dataSet.orgId, dataSet.spec, userName)
  }

  def addHash(dataSet: DataSet, key: String, hash: String) {
    update(MongoDBObject("_id" -> dataSet._id), MongoDBObject("$set" -> MongoDBObject(("hashes." + key) -> hash)))
  }

  def delete(dataSet: DataSet) {
    MetadataCache.get(dataSet.orgId, dataSet.spec, DataSetPlugin.ITEM_TYPE).removeAll()
    // todo: delete the other prefixes, not just none
    HubServices.basexStorages.getResource(dataSet.configuration).deleteCollection(dataSet, prefix = None)
    remove(MongoDBObject("_id" -> dataSet._id))
  }

  // ~~~ record handling

  def getSourceRecordCount(dataSet: DataSet): Long =
    HubServices.basexStorages.getResource(dataSet.configuration).count(dataSet, None)

  // ~~~ dataSet control

  def updateProcessingInstanceIdentifier(dataSet: DataSet, instanceIdentifier: Option[String]) {
    if (instanceIdentifier == None) {
      update(MongoDBObject("_id" -> dataSet._id), $unset("processingInstanceIdentifier"))
    } else {
      update(MongoDBObject("_id" -> dataSet._id), $set("processingInstanceIdentifier" -> instanceIdentifier.get))
    }
  }

  def updateState(dataSet: DataSet, state: DataSetState, userName: Option[String] = None, errorMessage: Option[String] = None) {
    if (errorMessage.isDefined) {
      update(MongoDBObject("_id" -> dataSet._id), $set(
        "state.name" -> state.name, "errorMessage" -> errorMessage.get
      ))
      DataSetEvent ! DataSetEvent.StateChanged(dataSet.orgId, dataSet.spec, state, userName)
      DataSetEvent ! DataSetEvent.Error(dataSet.orgId, dataSet.spec, errorMessage.get, userName)
    } else {
      update(MongoDBObject("_id" -> dataSet._id), $set("state.name" -> state.name) ++ $unset("errorMessage"))
      DataSetEvent ! DataSetEvent.StateChanged(dataSet.orgId, dataSet.spec, state, userName)
    }
  }

  // TODO we don't use those actually anymore, they're taking now directly from the configuration at indexing time
  // we may want to introduce collection-lavel facets and other things later on, when generifying the search / indexing component
  def updateIndexingControlState(dataSet: DataSet, mapping: String, facets: List[String], sortFields: List[String]) {
    update(
      MongoDBObject("_id" -> dataSet._id),
      $addToSet("idxMappings" -> mapping) ++ $set("idxFacets" -> facets, "idxSortFields" -> sortFields)
    )
  }

  def updateIndexingCount(dataSet: DataSet, count: Long) {
    update(
      MongoDBObject("_id" -> dataSet._id),
      MongoDBObject("$set" -> MongoDBObject("details.processedRecordCount" -> count))
    )
    DataSetEvent ! DataSetEvent.ProcessedRecordCountChanged(dataSet.orgId, dataSet.spec, count)
  }

  def updateRecordCount(dataSet: DataSet, count: Long) {
    update(
      MongoDBObject("_id" -> dataSet._id),
      MongoDBObject("$set" -> MongoDBObject("details.total_records" -> count))
    )
    DataSetEvent ! DataSetEvent.SourceRecordCountChanged(dataSet.orgId, dataSet.spec, count)
  }

  def invalidateHashes(dataSet: DataSet) {
    update(MongoDBObject("_id" -> dataSet._id), $unset("hashes"))
    // TODO fire appropriate event or state change event
  }

  // ~~~ OAI-PMH

  def getAllVisibleMetadataFormats(orgId: String, accessKey: Option[String]): List[RecordDefinition] = {
    val metadataFormats = findAll().flatMap {
      ds => ds.getVisibleMetadataSchemas(accessKey)
    }
    metadataFormats.toList.distinct
  }

  def getMetadataFormats(spec: String, orgId: String, accessKey: Option[String]): Seq[RecordDefinition] = {
    findBySpecAndOrgId(spec, orgId) match {
      case Some(ds) => ds.getVisibleMetadataSchemas(accessKey)
      case None => List[RecordDefinition]()
    }
  }

  // statistics

  def getMostRecentDataSetStatistics(implicit configuration: OrganizationConfiguration) = {
    DataSetStatistics.dao.find(MongoDBObject()).$orderby(MongoDBObject("_id" -> -1)).limit(1).toList.headOption
  }

  // links from the link-checker

  def getLinksFile(spec: String, orgId: String, prefix: String): Option[GridFSDBFile] = {
    hubFileStores.getResource(configuration).findOne(MongoDBObject(
      "orgId" -> orgId,
      "spec" -> spec,
      "schema" -> prefix,
      "hubFileType" -> "links"
    ))
  }

}

case class FactDefinition(name: String, prompt: String, tooltip: String, automatic: Boolean = false, options: Seq[String]) {
  def hasOptions = !options.isEmpty

  val opts = options.map(opt => (opt, opt))
}

case class Mapping(recordMapping: Option[String] = None, schemaPrefix: String, schemaVersion: String)

case class Details(
    name: String, // TODO this is repeated with the fact "name"...one day, unify
    total_records: Long = 0,
    processedRecordCount: Option[Long] = None,
    invalidRecordCount: Map[String, Long] = Map.empty,
    facts: BasicDBObject = new BasicDBObject()) {

  def getFactsAsText: String = {
    val builder = new StringBuilder
    facts foreach {
      fact => builder.append(fact._1).append("=").append(fact._2).append("\n")
    }
    builder.toString()
  }

}