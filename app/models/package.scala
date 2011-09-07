package models {

import com.novus.salat._
import play.Play
import com.mongodb.casbah.{MongoDB, MongoConnection}

package object salatContext {

  val connectionName = if(Play.configuration != null) Play.configuration.getProperty("db.mongo.name") else if(Play.mode == Play.Mode.DEV) "culturehub" else null

  val connection: MongoDB = MongoConnection()(connectionName)

  lazy val userCollection = connection("Users")
  lazy val groupCollection = connection("Groups")
  lazy val portalThemeCollection = connection("PortalThemes")
  lazy val emailTargetCollection = connection("EmailTargets") // TODO move to PortalTheme as subdocument
  lazy val dataSetsCollection = connection("Datasets")
  lazy val objectsCollection = connection("UserObjects") // the user contributed objects
  lazy val userCollectionsCollection = connection("UserCollections") // the collections made by users
  lazy val userStoriesCollection = connection("UserStories")
  lazy val harvestStepsCollection = connection("HarvestSteps")

  val RECORD_COLLECTION_PREFIX: String = "Records." // prefix for the dataset records saved
  val MONGO_ID: String = "_id" // mongo identifier we use

  implicit val ctx = new Context {
    val name = Some("PlaySalatContext")
  }

  def initSalat() {
    ctx.registerClassLoader(Play.classloader)
  }
}

}