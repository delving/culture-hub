package models {

import com.novus.salat._
import play.Play
import com.mongodb.casbah.{MongoDB, MongoConnection}
import play.Logger

package object salatContext {

  import com.mongodb.ServerAddress

  val connectionName = if(Play.configuration != null) Play.configuration.getProperty("db.cultureHub.name") else if(Play.mode == Play.Mode.DEV) "culturehub" else null

  val connection: MongoDB  =  if (Play.configuration.getProperty("mongo.test.context").toBoolean || Play.mode == Play.Mode.DEV) {
    Logger.info("Starting Mongo in Test Mode connecting to localhost:27017")
    MongoConnection()(connectionName)
  }
  else if (mongoServerAddresses.isEmpty || mongoServerAddresses.size > 2) {
    Logger.info("Starting Mongo in Replicaset Mode connecting to %s".format(mongoServerAddresses.mkString(", ")))
    MongoConnection(mongoServerAddresses)(connectionName)
  }
  else {
    Logger.info("Starting Mongo in Single Target Mode connecting to %s".format(mongoServerAddresses.head.toString))
    MongoConnection(mongoServerAddresses.head)(connectionName)
  }

  lazy val mongoServerAddresses: List[ServerAddress] = {
    List(1, 2, 3).map {
      serverNumber =>
        val host = Play.configuration.getProperty("mongo.server%d.host".format(serverNumber)).stripMargin
        val port = Play.configuration.getProperty("mongo.server%d.port".format(serverNumber)).stripMargin
        (host, port)
    }.filter(entry => !entry._1.isEmpty && !entry._2.isEmpty).map(entry => new ServerAddress(entry._1, entry._2.toInt))
  }

  lazy val userCollection = connection("Users")
  lazy val groupCollection = connection("Groups")
  lazy val portalThemeCollection = connection("PortalThemes")
  lazy val emailTargetCollection = connection("EmailTargets") // TODO move to PortalTheme as subdocument
  lazy val dataSetsCollection = connection("Datasets")
  lazy val objectsCollection = connection("UserObjects") // the user contributed objects
  lazy val userCollectionsCollection = connection("UserCollections") // the collections made by users
  lazy val labelsCollection = connection("UserLabels") // the labels made by users
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