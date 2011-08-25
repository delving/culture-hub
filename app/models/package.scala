package models {

import com.novus.salat._
import play.Play
import com.mongodb.casbah.{MongoDB, MongoConnection}

package object salatContext {

  val connection: MongoDB = MongoConnection()(Play.configuration.getProperty("db.mongo.name"))

  val userCollection = connection("Users")
  val groupCollection = connection("Groups")
  val portalThemeCollection = connection("PortalThemes")
  val emailTargetCollection = connection("EmailTargets") // TODO move to PortalTheme as subdocument
  val dataSetsCollection = connection("Datasets")
  val objectsCollection = connection("UserObjects") // the user contributed objects
  val userCollectionsCollection = connection("UserCollections") // the collections made by users
  val harvestStepsCollection = connection("HarvestSteps")

  val RECORD_COLLECTION_PREFIX: String = "Records." // prefix for the dataset records saved
  val MONGO_ID: String = "_id" // mongo identifier we use

  implicit val ctx = new Context {
    val name = Some("PlaySalatContext")
  }
  ctx.registerClassLoader(Play.classloader)
}

}