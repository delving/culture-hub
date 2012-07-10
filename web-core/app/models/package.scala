package models

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

import play.Play
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.MongoCollection
import com.mongodb.DBObject
import play.api.Play.current
import extensions.ConfigurationException
import com.mongodb.casbah.gridfs.GridFS

// TODO when it works, rename to "models"
package object mongoContext extends models.MongoContext {

  def getNode = current.configuration.getString("cultureHub.nodeName").getOrElse(throw ConfigurationException("No cultureHub.nodeName provided - this is terribly wrong."))

  // ~~~ mongo connections
  val connectionName = if(Play.isProd) {
    current.configuration.getString("cultureHub.db.name").getOrElse(throw ConfigurationException("Could not find database name under key 'db.cultureHub.name'"))
  } else if(Play.isDev) {
    "culturehub"
  } else if(Play.isTest) {
    "culturehub-TEST"
  } else {
    null
  }

  val geonamesConnection = createConnection("geonames")
  lazy val geonamesCollection = geonamesConnection("geonames")
  geonamesCollection.ensureIndex(MongoDBObject("name" -> 1))



  val connection = createConnection(connectionName)

  // ~~~ mongo collections

  val thingIndexes = Seq(
    MongoDBObject("user_id" -> 1, "visibility.value" -> 1, "deleted" -> 1),
    MongoDBObject("userName" -> 1, "visibility.value" -> 1, "deleted" -> 1),
    MongoDBObject("links.linkType" -> 1)
  )

  def addIndexes(collection: MongoCollection, indexes: Seq[DBObject], indexNames: Seq[String] = Seq.empty) {
    if(indexNames.size == indexes.size) {
      indexes.zipWithIndex.foreach {
        item => collection.ensureIndex(item._1, indexNames(item._2))
      }
    } else {
      indexes.foreach(collection.ensureIndex(_))
    }
  }

  lazy val hubUserCollection = connection("Users")
  hubUserCollection.ensureIndex(MongoDBObject("userName" -> 1, "isActive" -> 1))

  lazy val groupCollection = connection("Groups")

  lazy val emailTargetCollection = connection("EmailTargets") // TODO move to PortalTheme as subdocument

  lazy val dataSetsCollection = connection("Datasets")

  lazy val virtualCollectionsCollection = connection("VirtualCollections")
  lazy val virtualCollectionsRecordsCollection = connection("VirtualCollectionsRecords")

  lazy val linksCollection = connection("Links") // the links
  // TODO more link indexes!!
  linksCollection.ensureIndex(MongoDBObject("linkType" -> 1, "value" -> 1))

  // duplicate links check
  linksCollection.ensureIndex(MongoDBObject(
    "userName" -> 1,
    "linkType" -> 1,
    "value" -> 1,
    "from.id" -> 1, "from.hubType" -> 1, "from.uri" -> 1, "from.refType" -> 1,
    "to.id" -> 1, "to.hubType" -> 1, "to.uri" -> 1, "to.refType" -> 1), "uniqueLink")

  // DataSet findTo
  linksCollection.ensureIndex(MongoDBObject("to.uri" -> 1, "linkType" -> 1))

  lazy val cmsPages = connection("CMSPages")
  cmsPages.ensureIndex(MongoDBObject("_id" -> 1, "language" -> 1))

  lazy val cmsMenuEntries = connection("CMSMenuEntries")
  cmsMenuEntries.ensureIndex(MongoDBObject("orgId" -> 1, "theme" -> 1, "menuKey" -> 1))
  cmsMenuEntries.ensureIndex(MongoDBObject("orgId" -> 1, "theme" -> 1, "menuKey" -> 1, "parentKey" -> 1))

  lazy val drupalEntitiesCollecion = connection("drupalEntities")

  lazy val routeAccessCollection = connection("RouteAccess")

  val statisticsIndexes = Seq(
    MongoDBObject("orgId" -> 1, "key" -> 1)
  )

  lazy val providerStatisticsCollection = connection("ProviderStatistics")
  addIndexes(providerStatisticsCollection, statisticsIndexes)

  lazy val dataProviderStatisticsCollection = connection("DataProviderStatistics")
  addIndexes(dataProviderStatisticsCollection, statisticsIndexes)

  lazy val organizationStatisticsCollection = connection("OrganizationStatistics")
  addIndexes(organizationStatisticsCollection, statisticsIndexes)

  lazy val collectionStatisticsCollection = connection("CollectionStatistics")
  addIndexes(collectionStatisticsCollection, statisticsIndexes)

  lazy val statisticsRunCollection = connection("StatisticsRun")


  // dataSet stats

  val dataSetStatisticsContextIndexNames = Seq("context", "contextDataProvider", "contextProvider")

  val dataSetStatisticsContextIndexes = Seq(
    MongoDBObject("context.orgId" -> 1, "context.spec" -> 1, "context.uploadDate" -> 1),
    MongoDBObject("context.orgId" -> 1, "context.spec" -> 1, "context.uploadDate" -> 1, "context.dataProvider" -> 1),
    MongoDBObject("context.orgId" -> 1, "context.spec" -> 1, "context.uploadDate" -> 1, "context.provider" -> 1)
  )

  lazy val dataSetStatistics = connection("DataSetStatistics")
  addIndexes(dataSetStatistics, dataSetStatisticsContextIndexes, dataSetStatisticsContextIndexNames)

  lazy val fieldFrequencies = connection("DataSetStatisticsFieldFrequencies")
  addIndexes(fieldFrequencies, dataSetStatisticsContextIndexes, dataSetStatisticsContextIndexNames)

  lazy val fieldValues = connection("DataSetStatisticsFieldValues")
  addIndexes(fieldValues, dataSetStatisticsContextIndexes, dataSetStatisticsContextIndexNames)


  lazy val dataSetEventLogCollection = connection("DataSetEventLog")
  addIndexes(dataSetEventLogCollection, Seq(MongoDBObject("orgId" -> 1)))
  addIndexes(dataSetEventLogCollection, Seq(MongoDBObject("transientEvent" -> 1)))


  lazy val hubFileStore = GridFS(connection)


}