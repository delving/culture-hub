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

package models {

import play.Play
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoCollection, WriteConcern}

package object salatContext {

  def getNode = play.Play.configuration.getProperty("culturehub.nodeName")

  val connectionName = if(Play.configuration != null) Play.configuration.getProperty("db.cultureHub.name") else if(Play.mode == Play.Mode.DEV) "culturehub" else null

  val cloudConnectionName = if(Play.id == "test") "culturecloud-TEST" else "culturecloud"

  val connection = createConnection(connectionName)
  val commonsConnection = createConnection(cloudConnectionName)
  val geonamesConnection = createConnection("geonames")

  lazy val groupCollection = connection("Groups")
  lazy val portalThemeCollection = connection("PortalThemes")
  lazy val emailTargetCollection = connection("EmailTargets") // TODO move to PortalTheme as subdocument
  lazy val dataSetsCollection = connection("Datasets")
  lazy val objectsCollection = connection("UserObjects") // the user contributed objects
  objectsCollection.ensureIndex(MongoDBObject("collections" -> 1))
  lazy val userCollectionsCollection = connection("UserCollections") // the collections made by users
  lazy val linksCollection = connection("Links") // the links
  // TODO more link indexes!!
  linksCollection.ensureIndex(MongoDBObject("linkType" -> 1, "value" -> 1))
  lazy val userStoriesCollection = connection("UserStories")
  lazy val harvestStepsCollection = connection("HarvestSteps")
  lazy val drupalEntitiesCollecion = connection("drupalEntities")
  lazy val CoRefCollecion = connection("coRefs")

  lazy val organizationCollection = commonsConnection("Organizations")
  organizationCollection.ensureIndex(MongoDBObject("orgId" -> 1))

  lazy val userCollection = commonsConnection("Users")
  userCollection.ensureIndex(MongoDBObject("userName" -> 1))

  lazy val geonamesCollection = geonamesConnection("geonames")
  geonamesCollection.ensureIndex(MongoDBObject("name" -> 1))

  val MONGO_ID: String = "_id" // mongo identifier we use

  // http://api.mongodb.org/java/2.6/com/mongodb/WriteConcern.html
  val SAFE_WC = WriteConcern(1, 0, true)

  def wasUpdated(collection: MongoCollection) = {
    Option(collection.lastError().get("updateExisting")) match {
      case None => false
      case Some(e) => e.asInstanceOf[Boolean]
    }

  }

}

}