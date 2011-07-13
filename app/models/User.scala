package models

import com.novus.salat._
import com.mongodb.casbah.Imports._
import dao.SalatDAO
import models.salatContext._

case class User(firstName: String, lastName: String, email: String, password: String, displayName: String, isAdmin: Boolean = false) {
  val fullname = firstName + " " + lastName
}

object User extends SalatDAO[User, ObjectId](collection = userCollection) {

  val nobody: User = User("", "", "none@nothing.com", "", "Nobody", false)

  def connect(email: String, password: String) = {
    User.findOne(MongoDBObject("email" -> email, "password" -> password))
  }

  def existsWithEmail(email: String) = User.count(MongoDBObject("email" -> email)) == 0
  def existsWithDisplayName(displayName: String) = User.count(MongoDBObject("displayName" -> displayName)) == 0


}

