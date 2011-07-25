package models {

import com.novus.salat._
import play.Play
import com.mongodb.casbah.{MongoDB, MongoConnection}

package object salatContext {

  val connection: MongoDB = MongoConnection()(Play.configuration.getProperty("db.mongo.name"))

  val portalThemeCollection = connection("portalTheme")
  val emailTargetCollection = connection("emailTarget")
  val userCollection = connection("user")
  val groupCollection = connection("userGroup")
  val userReferenceCollection = connection("userReference")
  val dataSetsCollection = connection("Datasets")
  val harvestStepsCollection = connection("HarvestSteps")

  val RECORD_COLLECTION_PREFIX: String = "Records." // prefix for the dataset records saved
  val MONGO_ID: String = "_id" // mongo identifier we use

  implicit val ctx = new Context {
    override val typeHintStrategy = TypeHintStrategy(when = TypeHintFrequency.Always)
    val name = Some("PlaySalatContext")
  }
  ctx.registerClassLoader(Play.classloader)
}

}