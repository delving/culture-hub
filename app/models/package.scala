package models {

import play.Play
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoCollection, WriteConcern}

package object salatContext {

  def getNode = play.Play.configuration.getProperty("culturehub.nodeName")

  val connectionName = if(Play.configuration != null) Play.configuration.getProperty("db.cultureHub.name") else if(Play.mode == Play.Mode.DEV) "culturehub" else null

  val cloudConnectionName = "culturecloud"

  val connection = createConnection(connectionName)
  val commonsConnection = createConnection(cloudConnectionName)

  lazy val groupCollection = connection("Groups")
  lazy val portalThemeCollection = connection("PortalThemes")
  lazy val emailTargetCollection = connection("EmailTargets") // TODO move to PortalTheme as subdocument
  lazy val dataSetsCollection = connection("Datasets")
  lazy val objectsCollection = connection("UserObjects") // the user contributed objects
  lazy val userCollectionsCollection = connection("UserCollections") // the collections made by users
  lazy val labelsCollection = connection("UserLabels") // the labels made by users
  lazy val userStoriesCollection = connection("UserStories")
  lazy val harvestStepsCollection = connection("HarvestSteps")
  lazy val drupalEntitiesCollecion = connection("drupalEntities")
  lazy val CoRefCollecion = connection("coRefs")

  lazy val organizationCollection = commonsConnection("Organizations")
  organizationCollection.ensureIndex(MongoDBObject("orgId" -> 1))

  lazy val userCollection = commonsConnection("Users")
  userCollection.ensureIndex(MongoDBObject("userName" -> 1))

  val RECORD_COLLECTION_PREFIX: String = "Records." // prefix for the dataset records saved
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