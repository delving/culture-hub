package controllers

import models.User
import com.mongodb.DBObject
import com.codahale.jerkson.Json._
import play.mvc.results.Result

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Users extends DelvingController {

  import views.User._

  def index(user: String) = {
    val u = getUser(user)
    html.index(username = u.reference.username)
  }

  def listAsTokens: Result = {
    // list all users as tokens (for auto-completion)
    // in order to adhere with the multi-type rendering we might want to combine this with a HTML action at some point (though listing all users does not seem to make much sense)

    val userTokens: List[Token] = for(u: DBObject <- User.findAllIdName) yield {
      Token(id = u.get("reference").asInstanceOf[DBObject].get("id").toString, name = u.get("firstName") + " " + u.get("lastName"))
    }

    Json(generate(userTokens))

  }

}