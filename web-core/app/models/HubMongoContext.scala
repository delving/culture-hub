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

import _root_.util.OrganizationConfigurationHandler
import com.mongodb.casbah.Imports._
import com.mongodb.DBObject
import com.mongodb.casbah.gridfs.GridFS
import play.api.Play
import play.api.Play.current

object HubMongoContext extends HubMongoContext {

  val CONFIG_DB = "configurationDatabaseName"
}

trait HubMongoContext extends models.MongoContext {

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

  lazy val fileStoreCache: Map[String, GridFS] = OrganizationConfigurationHandler.organizationConfigurations.map { dc =>
    (dc.objectService.fileStoreDatabaseName -> GridFS(createConnection(dc.objectService.fileStoreDatabaseName)))
  }.toMap

  lazy val imageCacheStoreCache: Map[String, GridFS] = OrganizationConfigurationHandler.organizationConfigurations.map { dc =>
    (dc.objectService.imageCacheDatabaseName -> GridFS(createConnection(dc.objectService.imageCacheDatabaseName)))
  }.toMap

  def imageCacheStore(configuration: OrganizationConfiguration) = imageCacheStoreCache(configuration.objectService.imageCacheDatabaseName)
  def fileStore(configuration: OrganizationConfiguration) = fileStoreCache(configuration.objectService.fileStoreDatabaseName)

  lazy val hubFileStore: Map[OrganizationConfiguration, GridFS] = {
    OrganizationConfigurationHandler.organizationConfigurations.map(c => (c -> GridFS(mongoConnections(c)))).toMap
  }

  lazy val mongoConnections: Map[OrganizationConfiguration, MongoDB] =
    OrganizationConfigurationHandler.organizationConfigurations.map {
      config => (config -> createConnection(config.mongoDatabase))
    }.toMap

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