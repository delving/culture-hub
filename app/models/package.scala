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
import com.mongodb.DBObject

package object salatContext {

  val MONGO_ID: String = "_id" // mongo identifier we use

  def getNode = play.Play.configuration.getProperty("culturehub.nodeName")

  // ~~~ mongo connections
  val connectionName = if(Play.configuration != null) Play.configuration.getProperty("db.cultureHub.name") else if(Play.mode == Play.Mode.DEV) "culturehub" else null

  val cloudConnectionName = if(Play.id == "test") "culturecloud-TEST" else "culturecloud"

  val connection = createConnection(connectionName)
  val commonsConnection = createConnection(cloudConnectionName)
  val geonamesConnection = createConnection("geonames")

  // ~~~ mongo collections

  val thingIndexes = Seq(
    MongoDBObject("userName" -> 1, "visibility.value" -> 1, "deleted" -> 1),
    MongoDBObject("links.linkType" -> 1)
  )
  
  def addIndexes(collection: MongoCollection, indexes: Seq[DBObject]) {
    indexes.foreach(collection.ensureIndex(_))
  }

  lazy val groupCollection = connection("Groups")

  lazy val portalThemeCollection = connection("PortalThemes")

  lazy val emailTargetCollection = connection("EmailTargets") // TODO move to PortalTheme as subdocument

  lazy val dataSetsCollection = connection("Datasets")

  lazy val objectsCollection = connection("UserObjects") // the user contributed objects
  addIndexes(objectsCollection, thingIndexes)

  lazy val userCollectionsCollection = connection("UserCollections") // the collections made by users
  addIndexes(userCollectionsCollection, thingIndexes)
  userStoriesCollection.ensureIndex(MongoDBObject("isBookmarksCollection" -> 1))

  lazy val userStoriesCollection = connection("UserStories")
  addIndexes(userStoriesCollection, thingIndexes)

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

  lazy val harvestStepsCollection = connection("HarvestSteps")

  lazy val drupalEntitiesCollecion = connection("drupalEntities")

  lazy val CoRefCollecion = connection("coRefs")


  lazy val organizationCollection = commonsConnection("Organizations")
  organizationCollection.ensureIndex(MongoDBObject("orgId" -> 1))

  lazy val userCollection = commonsConnection("Users")
  userCollection.ensureIndex(MongoDBObject("userName" -> 1, "isActive" -> 1))

  lazy val geonamesCollection = geonamesConnection("geonames")
  geonamesCollection.ensureIndex(MongoDBObject("name" -> 1))

  def wasUpdated(collection: MongoCollection) = {
    Option(collection.lastError().get("updateExisting")) match {
      case None => false
      case Some(e) => e.asInstanceOf[Boolean]
    }

  }

}

}