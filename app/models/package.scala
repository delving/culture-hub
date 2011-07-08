package models {

import com.novus.salat._
import play.Play
import com.mongodb.casbah.{MongoDB, MongoConnection}

package object salatContext {

  val connection: MongoDB = MongoConnection()("culturehub")

  val portalThemeCollection = connection("portalTheme")
  val emailTargetCollection = connection("emailTarget")
  val userCollection = connection("user")
  
  implicit val ctx = new Context {
    val name = Some("PlaySalatContext")
  }
  ctx.registerClassLoader(Play.classloader)
}

}