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

import _root_.util.DomainConfigurationHandler
import com.mongodb.casbah.Imports._
import com.mongodb.DBObject
import com.mongodb.casbah.gridfs.GridFS

package object mongoContext extends models.MongoContext {

  val geonamesConnection = createConnection("geonames")
  lazy val geonamesCollection = geonamesConnection("geonames")
  geonamesCollection.ensureIndex(MongoDBObject("name" -> 1))

  def addIndexes(collection: MongoCollection, indexes: Seq[DBObject], indexNames: Seq[String] = Seq.empty) {
    if(indexNames.size == indexes.size) {
      indexes.zipWithIndex.foreach {
        item => collection.ensureIndex(item._1, indexNames(item._2))
      }
    } else {
      indexes.foreach(collection.ensureIndex(_))
    }
  }

  lazy val hubFileStore: Map[DomainConfiguration, GridFS] = {
    DomainConfigurationHandler.domainConfigurations.map(c => (c -> GridFS(createConnection(c.mongoDatabase)))).toMap
  }


  // ~~~ shared indexes

  val dataSetStatisticsContextIndexes = Seq(
    MongoDBObject("context.orgId" -> 1, "context.spec" -> 1, "context.uploadDate" -> 1),
    MongoDBObject("context.orgId" -> 1, "context.spec" -> 1, "context.uploadDate" -> 1, "context.dataProvider" -> 1),
    MongoDBObject("context.orgId" -> 1, "context.spec" -> 1, "context.uploadDate" -> 1, "context.provider" -> 1)
  )
  val dataSetStatisticsContextIndexNames = Seq("context", "contextDataProvider", "contextProvider")



}