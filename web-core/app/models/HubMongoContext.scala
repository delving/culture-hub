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

import util.{ OrganizationConfigurationResourceHolder, OrganizationConfigurationHandler }
import com.mongodb.casbah.Imports._
import com.mongodb.DBObject
import com.mongodb.casbah.gridfs.GridFS
import play.api.{ Logger, Play }
import play.api.Play.current

object HubMongoContext extends HubMongoContext {

  val CONFIG_DB = "configurationDatabaseName"

  def init() {
    OrganizationConfigurationHandler.registerResourceHolder(mongoConnections, initFirst = true)
    OrganizationConfigurationHandler.registerResourceHolder(fileStores)
    OrganizationConfigurationHandler.registerResourceHolder(imageCacheStores)
    OrganizationConfigurationHandler.registerResourceHolder(hubFileStores)
  }

}

trait HubMongoContext extends models.MongoContext {

  val log = Logger("CultureHub")

  lazy val configurationConnection = createConnection(Play.application.configuration.getString(HubMongoContext.CONFIG_DB).getOrElse("culturehub-configuration"))
  lazy val configurationCollection = configurationConnection("configurations")

  val geonamesConnection = createConnection("geonames")
  lazy val geonamesCollection = geonamesConnection("geonames")
  geonamesCollection.ensureIndex(MongoDBObject("name" -> 1))

  def addIndexes(collection: MongoCollection, indexes: Seq[DBObject], indexNames: Seq[String] = Seq.empty) {
    if (indexNames.size == indexes.size) {
      indexes.zipWithIndex.foreach {
        item => collection.ensureIndex(item._1, indexNames(item._2))
      }
    } else {
      indexes.foreach(collection.ensureIndex(_))
    }
  }

  val fileStores = new OrganizationConfigurationResourceHolder[ObjectServiceConfiguration, GridFS]("fileStores") {

    protected def resourceConfiguration(configuration: OrganizationConfiguration): ObjectServiceConfiguration = configuration.objectService

    protected def onAdd(resourceConfiguration: ObjectServiceConfiguration): Option[GridFS] = Some(GridFS(createConnection(resourceConfiguration.fileStoreDatabaseName)))

    protected def onRemove(removed: GridFS) {}
  }

  val imageCacheStores = new OrganizationConfigurationResourceHolder[ObjectServiceConfiguration, GridFS]("imageCacheStores") {

    protected def resourceConfiguration(configuration: OrganizationConfiguration): ObjectServiceConfiguration = configuration.objectService

    protected def onAdd(resourceConfiguration: ObjectServiceConfiguration): Option[GridFS] = Some(GridFS(createConnection(resourceConfiguration.imageCacheDatabaseName)))

    protected def onRemove(removed: GridFS) {}
  }

  val mongoConnections = new OrganizationConfigurationResourceHolder[String, MongoDB]("mongoConnections") {

    protected def resourceConfiguration(configuration: OrganizationConfiguration): String = configuration.mongoDatabase

    protected def onAdd(resourceConfiguration: String): Option[MongoDB] = {
      try {
        Some(createConnection(resourceConfiguration))
      } catch {
        case t: Throwable =>
          log.error(s"Couldn't open connection to MongoDB database $resourceConfiguration", t)
          None
      }
    }

    protected def onRemove(removed: MongoDB) {
      // TODO find a way to close here!!
    }
  }

  val hubFileStores = new OrganizationConfigurationResourceHolder[OrganizationConfiguration, GridFS]("hubFileStores") {

    protected def resourceConfiguration(configuration: OrganizationConfiguration): OrganizationConfiguration = configuration
    protected def onAdd(resourceConfiguration: OrganizationConfiguration): Option[GridFS] = {
      Some(GridFS(mongoConnections.getResource(resourceConfiguration)))
    }

    protected def onRemove(removed: GridFS) {}
  }

  def imageCacheStore(configuration: OrganizationConfiguration) = HubMongoContext.imageCacheStores.getResource(configuration)
  def fileStore(configuration: OrganizationConfiguration) = HubMongoContext.fileStores.getResource(configuration)

  // ~~~ shared indexes

  val dataSetStatisticsContextIndexes = Seq(
    MongoDBObject("context.orgId" -> 1, "context.spec" -> 1, "context.uploadDate" -> 1),
    MongoDBObject("context.orgId" -> 1, "context.spec" -> 1, "context.uploadDate" -> 1, "context.dataProvider" -> 1),
    MongoDBObject("context.orgId" -> 1, "context.spec" -> 1, "context.uploadDate" -> 1, "context.provider" -> 1)
  )
  val dataSetStatisticsContextIndexNames = Seq("context", "contextDataProvider", "contextProvider")

  // ~~~ DoS identifiers

  // ~~ images uploaded directly via culturehub
  val ITEM_POINTER_FIELD = "object_id" // pointer to the owning item, for cleanup
  val ITEM_TYPE = "object_type" // classifier for the file type
  val FILE_POINTER_FIELD = "original_file" // pointer from a thumbnail to its parent file
  val IMAGE_ITEM_POINTER_FIELD = "image_object_id" // pointer from an chosen image to its item, useful to lookup an image by item ID
  val THUMBNAIL_ITEM_POINTER_FIELD = "thumbnail_object_id" // pointer from a chosen thumbnail to its item, useful to lookup a thumbnail by item ID

  // ~~ images stored locally (file system)
  val IMAGE_ID_FIELD = "file_id" // identifier (mostly file name without extension) of an image, or of a thumbnail (to refer to the parent image)
  val ORIGIN_PATH_FIELD = "origin_path" // path from where this thumbnail has been ingested

  // ~~~ types
  val FILE_TYPE_UNATTACHED = "unattached"

}