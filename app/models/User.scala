package models

import com.novus.salat._
import com.mongodb.casbah.Imports._
import dao.SalatDAO
import models.play_salat_context._

case class User(email: String, password: String, fullname: String, displayName: String, isAdmin: Boolean = false)

object User extends SalatDAO[User, ObjectId](collection = MongoConnection()("culturehub")("user")) {

  val nobody: User = User("none@nothing.com", "", "Nobody", "", false)

  def connect(email: String, password: String) = {
    User.findOne(MongoDBObject("email" -> email, "password" -> password))
  }

}

