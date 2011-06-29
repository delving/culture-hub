package models

import play.db.anorm._
import play.db.anorm.defaults._

case class User(
                id: Pk[Long], email: String, password: String, fullname: String, displayName: String, isAdmin: Boolean
               )

object User extends Magic[User] {

  val nobody: User = User(null, "none@nothing.com", "", "Nobody", "", false)

  def connect(email: String, password: String) = {
    User.find("email = {email} and password = {password}")
            .on("email" -> email, "password" -> password)
            .first()
  }

}

