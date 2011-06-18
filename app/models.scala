package models

import play.db.anorm._
import play.db.anorm.SqlParser._
import play.db.anorm.defaults._
 
// User

case class User(
    id: Pk[Long], 
    email: String, password: String, fullname: String, isAdmin: Boolean
)

object User extends Magic[User] {
    
    def connect(email: String, password: String) = {
        User.find("email = {email} and password = {password}")
            .on("email" -> email, "password" -> password)
            .first()
    }
    
}

